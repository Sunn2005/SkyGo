package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.BookingQuote;
import com.skygo.backend.booking.model.SeatHold.Direction;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PassengerValidationService {
  private static final Set<String> COUNTRIES=Set.of(Locale.getISOCountries());
  private final Clock clock;
  public PassengerValidationService(Clock clock) { this.clock=clock; }
  public record Validated(List<PassengerRequest> passengers,BookingReviewRequest.Contact contact) {
    @Override public String toString() { return "Validated[redacted]"; }
  }
  public Validated validate(List<PassengerRequest> passengers,BookingReviewRequest.Contact contact,BookingQuote quote) {
    var out=quote.legs().stream().filter(l->l.direction()==Direction.OUTBOUND).findFirst().orElseThrow();
    var departure=out.departureAt().atZone(ZoneId.of(out.origin().timeZone())).toLocalDate();
    var today=clock.instant().atZone(ZoneId.of(out.origin().timeZone())).toLocalDate();
    var lastDate=quote.legs().stream().map(l->l.departureAt().atZone(ZoneId.of(l.origin().timeZone())).toLocalDate())
        .max(LocalDate::compareTo).orElseThrow();
    boolean international=quote.legs().stream().anyMatch(l->l.routeType().equals("INTERNATIONAL"));
    var ids=new HashSet<String>();
    var counts=new EnumMap<PassengerType,Integer>(PassengerType.class);
    var normalized=new ArrayList<PassengerRequest>();
    for(int i=0;i<passengers.size();i++) {
      var p=passengers.get(i);
      String field="passengers["+i+"]";
      String id=p.clientPassengerId().strip();
      if(!ids.add(id)) throw invalid(field+".clientPassengerId","Mã hành khách phải duy nhất.");
      if(p.dateOfBirth().isAfter(today)) throw invalid(field+".dateOfBirth","Ngày sinh không được ở tương lai.");
      int age=Period.between(p.dateOfBirth(),departure).getYears();
      boolean validAge=switch(p.passengerType()) {case ADULT->age>=12;case CHILD->age>=2&&age<12;case INFANT->age>=0&&age<2;};
      if(!validAge) throw new BookingException(422,"PASSENGER_AGE_MISMATCH","Ngày sinh không khớp nhóm hành khách.",field+".dateOfBirth",null,null);
      if((p.passengerType()==PassengerType.ADULT)!=(p.title()!=null))
        throw invalid(field+".title","Danh xưng chỉ bắt buộc cho người lớn.");
      var d=p.document();
      country(d.issuingCountryCode(),field+".document.issuingCountryCode");
      if(international && d.documentType()!=DocumentType.PASSPORT)
        throw invalid(field+".document.documentType","Hành trình quốc tế yêu cầu hộ chiếu.");
      if(d.documentType()==DocumentType.PASSPORT) {
        country(d.nationalityCountryCode(),field+".document.nationalityCountryCode");
        if(d.expiresOn()==null || d.expiresOn().isBefore(lastDate))
          throw invalid(field+".document.expiresOn","Hộ chiếu phải còn hạn đến ngày khởi hành cuối cùng.");
      } else if(d.nationalityCountryCode()!=null || d.expiresOn()!=null)
        throw invalid(field+".document","Giấy tờ định danh cá nhân không dùng trường quốc tịch hoặc hạn hộ chiếu.");
      counts.merge(p.passengerType(),1,Integer::sum);
      normalized.add(new PassengerRequest(id,p.passengerType(),p.title(),p.givenNames().strip(),p.surname().strip(),
          p.dateOfBirth(),p.gender(),new Document(d.documentType(),d.number().strip(),d.issuingCountryCode(),
              d.nationalityCountryCode(),d.expiresOn())));
    }
    var expected=quote.passengerCounts();
    if(counts.getOrDefault(PassengerType.ADULT,0)!=expected.adults()
        || counts.getOrDefault(PassengerType.CHILD,0)!=expected.children()
        || counts.getOrDefault(PassengerType.INFANT,0)!=expected.infants())
      throw invalid("passengers","Số hành khách từng nhóm phải khớp lượt giữ chỗ.");
    String phone=contact.phoneNumber().strip().replaceAll("[\\s()-]","");
    if(phone.matches("0[35789][0-9]{8}")) phone="+84"+phone.substring(1);
    if(!phone.matches("\\+[1-9][0-9]{6,14}"))
      throw invalid("contact.phoneNumber","Số liên hệ phải là số di động Việt Nam hoặc số quốc tế + gồm 7–15 chữ số.");
    return new Validated(List.copyOf(normalized),new BookingReviewRequest.Contact(contact.email().strip(),phone));
  }
  private void country(String code,String field) {
    if(code==null || !COUNTRIES.contains(code)) throw invalid(field,"Mã quốc gia ISO không hợp lệ.");
  }
  private BookingException invalid(String field,String message) { return BookingException.invalid(field,message); }
  public static String mask(String number) {
    return number.length()<=4?"****":"*".repeat(number.length()-4)+number.substring(number.length()-4);
  }
}
