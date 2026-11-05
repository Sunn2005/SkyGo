package com.skygo.backend.home.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.home.contract.HomeResponse;
import com.skygo.backend.home.service.HomeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
  private final HomeService service;

  public HomeController(HomeService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/home")
  public DataResponse<HomeResponse> home(@AuthenticationPrincipal AuthSession session) {
    return new DataResponse<>(service.home(session == null ? null : session.user().id()));
  }
}
