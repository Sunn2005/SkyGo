package com.skygo.backend.flight.service;

import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.flight.contract.FavoriteFlightResponse;
import com.skygo.backend.flight.contract.FlightSearchResponse.Airline;
import com.skygo.backend.flight.repository.FavoriteFlightRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FavoriteFlightService {
  private final FavoriteFlightRepository repository;
  private final Clock clock;
  private final Airline airline;
  public FavoriteFlightService(FavoriteFlightRepository repository, Clock clock,
      @Value("${skygo.airline.code}") String code, @Value("${skygo.airline.name}") String name) {
    this.repository=repository; this.clock=clock; this.airline=new Airline(code,name);
  }
  @Transactional
  public void save(long user,String id) { repository.save(user,id,clock.instant()); }
  @Transactional
  public void delete(long user,String id) { repository.delete(user,id); }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public FavoriteFlightResponse list(long user,String page,String size) {
    return repository.list(user,integer("page",page,0,Integer.MAX_VALUE),
        integer("size",size,1,100),clock.instant(),airline);
  }
  private int integer(String field,String value,int min,int max) {
    try { int n=Integer.parseInt(value); if(n>=min && n<=max)return n; }
    catch(NumberFormatException ignored) {}
    throw BookingException.invalid(field,field+" phải là số nguyên từ "+min+" đến "+max+".");
  }
}
