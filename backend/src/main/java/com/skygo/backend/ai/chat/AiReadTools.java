package com.skygo.backend.ai.chat;

import com.skygo.backend.ai.chat.AiToolInputs.*;
import com.skygo.backend.ai.chat.contract.AiChatResponse.*;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.dao.DataAccessException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/** Created afresh for EACH HTTP request. Owner identity never appears in the model's input schema. */
public final class AiReadTools {
  private static final JsonMapper JSON = JsonMapper.builder()
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
      .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
  private final AiReadRepository repository;
  private final AiKnowledgeReader knowledge;
  private final AiAirportResolver.Session airports;
  private final Validator validator;
  private final Long owner;
  private final Map<String, ToolCallback> tools = new LinkedHashMap<>();
  private final Map<String, String> memo = new HashMap<>();
  private final Map<String, Source> sources = new LinkedHashMap<>();
  private final List<FlightResult> flights = new ArrayList<>();
  private final List<BookingResult> bookings = new ArrayList<>();
  private final List<Clarification> clarifications = new ArrayList<>();
  private boolean loginRequired;
  private boolean evidence;

  public AiReadTools(AiReadRepository repository, AiKnowledgeReader knowledge, AiAirportResolver airportResolver,
      Validator validator, Long owner) {
    this.repository = repository;
    this.knowledge = knowledge;
    // Schema-only diagnostic probes supply null dependencies and never execute tools.
    this.airports = airportResolver == null ? null : airportResolver.newSession();
    this.validator = validator;
    this.owner = owner;
    register("searchFlights", "Tra cứu SQL chuyến bay một chiều theo tuyến/ngày, so sánh hạng vé hoặc giá rẻ nhất. "
        + "Nhận mã IATA hoặc nguyên tên thành phố/sân bay khách nói; backend đối chiếu danh mục rồi dùng RAG sân bay nếu tên chưa khớp. "
        + "Không tự đoán IATA. Giá partyTotal đã gồm VAT cho đúng số hành khách, chưa gồm hành lý mua thêm. "
        + "Thiếu ngày/tuyến phải gọi askForDetails. Không suy đoán ngày hoặc số hành khách khách đã yêu cầu.", """
        {"type":"object","properties":{
          "origin":{"type":"string"},"destination":{"type":"string"},
          "departureDate":{"type":"string","description":"Ngày bay YYYY-MM-DD theo giờ sân bay đi"},
          "adults":{"type":"integer","minimum":1,"maximum":9},
          "children":{"type":"integer","minimum":0,"maximum":8},
          "infants":{"type":"integer","minimum":0,"maximum":9},
          "cabin":{"type":"string","enum":["ANY","ECONOMY","BUSINESS"]},
          "sort":{"type":"string","enum":["PRICE_ASC","DEPARTURE_ASC","DURATION_ASC"]},
          "limit":{"type":"integer","minimum":1,"maximum":10}},
          "required":["origin","destination","departureDate"],"additionalProperties":false}
        """, FlightQuery.class);
    register("searchRecentBookings", "Đọc lịch sử đặt vé gần đây của CHÍNH tài khoản đang đăng nhập, SQL sắp xếp mới nhất trước. "
        + "Mặc định 15 ngày, 3 đơn. Máy chủ tự xác định chủ sở hữu. Khách chưa đăng nhập nhận LOGIN_REQUIRED. "
        + "Không có chức năng xem đơn của người khác.", """
        {"type":"object","properties":{
          "days":{"type":"integer","minimum":1,"maximum":365},
          "limit":{"type":"integer","minimum":1,"maximum":10},
          "status":{"type":"string","enum":["ALL","PAID","PENDING"]}},"additionalProperties":false}
        """, BookingQuery.class);
    register("searchKnowledge", "Tìm tài liệu RAG công khai về sân bay, tuyến, hành lý, suất ăn và chính sách vé SkyGo. "
        + "Kết quả là snapshot có thời gian lập chỉ mục. KHÔNG dùng công cụ này để xác nhận giá, ghế, lịch sử hoặc trạng thái hiện tại.", """
        {"type":"object","properties":{"query":{"type":"string","maxLength":500}},
          "required":["query"],"additionalProperties":false}
        """, KnowledgeQuery.class);
    register("askForDetails", "Yêu cầu khách bổ sung thông tin thiếu hoặc mô tả rõ câu hỏi; dùng trước khi truy vấn nếu thiếu tuyến/ngày. "
        + "Không tự đặt ngày bay hoặc đoán sân bay mơ hồ.", """
        {"type":"object","properties":{"fields":{"type":"array","minItems":1,"maxItems":5,
          "items":{"type":"string","enum":["origin","destination","departureDate","passengerCounts","question"]}}},
          "required":["fields"],"additionalProperties":false}
        """, DetailsQuery.class);
  }

  private void register(String name, String description, String schema, Class<?> inputType) {
    var definition = ToolDefinition.builder().name(name).description(description).inputSchema(schema).build();
    tools.put(name, new ToolCallback() {
      @Override public ToolDefinition getToolDefinition() { return definition; }
      @Override public String call(String input) { return execute(name, input, inputType); }
    });
  }

  public List<ToolCallback> callbacks() { return List.copyOf(tools.values()); }

  public String execute(String name, String arguments) {
    var tool = tools.get(name);
    if (tool == null) throw new AiChatException(502, "AI_INVALID_TOOL", "Model yêu cầu chức năng không được hỗ trợ.");
    return tool.call(arguments);
  }

  private String execute(String name, String arguments, Class<?> inputType) {
    if (arguments == null || arguments.length() > 4000) return invalid("question");
    final Object input;
    try {
      input = JSON.readValue(arguments, inputType);
      if (input == null) return invalid("question");
      var violations = validator.validate(input);
      if (!violations.isEmpty()) return invalid(violations.stream().map(v -> v.getPropertyPath().toString()).sorted().findFirst().orElse("question"));
    } catch (RuntimeException ex) { return invalid("question"); }
    String memoKey = name + ":" + JSON.writeValueAsString(input);
    if (memo.containsKey(memoKey)) return memo.get(memoKey);
    try {
      Object result;
      if (input instanceof FlightQuery query) {
        // This short SQL call returns before embedding/vector calls begin. The
        // final flight query then revalidates the canonical codes in a new SQL read.
        var catalog = repository.airports();
        final Airport origin;
        final Airport destination;
        try {
          origin = Objects.requireNonNull(airports).resolve(query.origin(), "origin", catalog);
          destination = airports.resolve(query.destination(), "destination", catalog);
        } finally {
          if (airports != null) airports.sources().forEach(this::addSource);
        }
        var canonical = new FlightQuery(origin.code(), destination.code(), query.departureDate(),
            query.adults(), query.children(), query.infants(), query.cabin(), query.sort(), query.limit());
        var found = repository.flights(canonical);
        flights.add(found);
        evidence |= !found.options().isEmpty();
        for (var option : found.options()) addSource(new Source("MARIADB", "flight:" + option.flightId() + ":" + option.cabinClass(),
            option.flightNumber() + " " + option.cabinClass() + " " + found.departureDate(), found.asOf()));
        result = found;
      } else if (input instanceof BookingQuery query) {
        if (owner == null) {
          loginRequired = true;
          return JSON.writeValueAsString(Map.of("status", "LOGIN_REQUIRED", "message", "Vui lòng đăng nhập để xem lịch sử đặt vé."));
        }
        var found = repository.bookings(owner, query);
        bookings.add(found);
        evidence |= !found.bookings().isEmpty();
        found.bookings().forEach(b -> addSource(new Source("MARIADB", "booking:" + b.bookingId(),
            "Đơn " + b.bookingCode(), found.asOf())));
        result = found;
      } else if (input instanceof KnowledgeQuery query) {
        var hits = knowledge.search(query.query());
        evidence |= !hits.isEmpty();
        hits.forEach(h -> addSource(new Source("MARIADB_VECTOR", h.id(), h.title(), h.indexedAt())));
        result = hits;
      } else {
        var details = (DetailsQuery) input;
        if (details.fields() == null || details.fields().isEmpty() || details.fields().size() > 5) return invalid("question");
        for (String field : details.fields()) {
          String message = switch (field == null ? "" : field) {
            case "origin" -> "Bạn muốn khởi hành từ sân bay hoặc thành phố nào?";
            case "destination" -> "Bạn muốn đến sân bay hoặc thành phố nào?";
            case "departureDate" -> "Bạn muốn bay ngày nào?";
            case "passengerCounts" -> "Có bao nhiêu người lớn, trẻ em và em bé?";
            default -> "Bạn muốn tra cứu chuyến bay, chính sách hay lịch sử đặt vé?";
          };
          clarifications.add(new Clarification(field, message, List.of()));
        }
        return JSON.writeValueAsString(Map.of("status", "NEEDS_CLARIFICATION", "clarifications", clarifications));
      }
      String serialized = JSON.writeValueAsString(Map.of("data", result, "sources", sources()));
      memo.put(memoKey, serialized);
      return serialized;
    } catch (AiClarification ex) {
      clarifications.add(ex.detail());
      return JSON.writeValueAsString(Map.of("status", "NEEDS_CLARIFICATION", "clarification", ex.detail()));
    } catch (DataAccessException ex) {
      throw new AiChatException(503, "AI_DATA_UNAVAILABLE", "Chưa đọc được dữ liệu SkyGo. Vui lòng thử lại sau.");
    }
  }

  private String invalid(String field) {
    var detail = new Clarification(field, "Vui lòng bổ sung hoặc kiểm tra thông tin tra cứu: " + field + ".", List.of());
    clarifications.add(detail);
    return JSON.writeValueAsString(Map.of("status", "NEEDS_CLARIFICATION", "clarification", detail));
  }
  private void addSource(Source source) { sources.putIfAbsent(source.type() + ":" + source.id(), source); }
  public List<Source> sources() { return List.copyOf(sources.values()); }
  public List<FlightResult> flights() { return List.copyOf(flights); }
  public List<BookingResult> bookings() { return List.copyOf(bookings); }
  public List<Clarification> clarifications() { return List.copyOf(clarifications); }
  public boolean loginRequired() { return loginRequired; }
  public boolean hasEvidence() { return evidence; }
  public record DetailsQuery(
      @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min = 1, max = 5)
      List<@jakarta.validation.constraints.NotBlank
          @jakarta.validation.constraints.Pattern(regexp = "origin|destination|departureDate|passengerCounts|question") String> fields) {}
}
