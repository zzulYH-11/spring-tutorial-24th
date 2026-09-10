package com.ceos24.spring_boot.test;

import com.ceos24.spring_boot.test.dto.response.TestNameResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/tests")
public class TestController {

    private final TestService testService;

    @GetMapping
    public TestNameResponse findAllTests() {
        return testService.findAllTests();
    }
}