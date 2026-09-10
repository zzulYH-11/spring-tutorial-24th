package com.ceos24.spring_boot.test.dto.response;

import com.ceos24.spring_boot.test.Test;

import java.util.List;

public record TestNameResponse(
        List<String> names
) {
    public static TestNameResponse from(List<Test> tests) {
        return new TestNameResponse(tests.stream().map(Test::getName).toList());
    }
}
