package com.b2bmarket.authentication.internal;

import com.b2bmarket.businesses.BusinessDtos.Membership;
import com.b2bmarket.businesses.BusinessService;
import com.b2bmarket.users.CurrentUser;
import com.b2bmarket.users.UserProfile;
import com.b2bmarket.users.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person: their profile and the businesses they belong to. */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

  record MeResponse(UserProfile profile, List<Membership> businesses) {}

  /** Both fields optional; only the fields present are changed. A blank name clears it. */
  record UpdateProfileRequest(
      @Size(max = 100) String displayName,
      @Pattern(regexp = "en|te|hi", message = "must be en, te or hi") String preferredLocale) {}

  private final UserService users;
  private final BusinessService businesses;

  MeController(UserService users, BusinessService businesses) {
    this.users = users;
    this.businesses = businesses;
  }

  @GetMapping
  MeResponse me(CurrentUser caller) {
    return new MeResponse(
        users.profile(caller.userId()), businesses.membershipsOf(caller.userId()));
  }

  @PatchMapping
  UserProfile updateProfile(CurrentUser caller, @Valid @RequestBody UpdateProfileRequest request) {
    return users.updateProfile(caller.userId(), request.displayName(), request.preferredLocale());
  }
}
