package com.reveila.spring.system;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebForwardController {
    // This regex matches any path that DOES NOT start with 'api' and has no dots
    @GetMapping(value = "{path:^(?!api).*$:[^\\.]*}")
    public String redirect() {
        return "forward:/index.html";
    }
}