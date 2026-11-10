package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.BookingListResponse;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.repository.BookingListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingListService {
  private final BookingListRepository repository;

  public BookingListService(BookingListRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public BookingListResponse list(long userId, String pageValue, String sizeValue, String status) {
    int page = integer("page", pageValue, 0, Integer.MAX_VALUE);
    int size = integer("size", sizeValue, 1, 100);
    if (!java.util.Set.of("all", "paid", "pending").contains(status))
      throw BookingException.invalid("status", "Bộ lọc đặt chỗ không hợp lệ.");
    return repository.list(userId, page, size, status);
  }

  private int integer(String field, String value, int min, int max) {
    try {
      int result = Integer.parseInt(value);
      if (result >= min && result <= max) return result;
    } catch (NumberFormatException ignored) {
      // Invalid and out-of-range values share the same validation response.
    }
    throw BookingException.invalid(field,
        field + " phải là số nguyên từ " + min + " đến " + max + ".");
  }
}
