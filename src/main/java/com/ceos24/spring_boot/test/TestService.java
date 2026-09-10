package com.ceos24.spring_boot.test;

import com.ceos24.spring_boot.test.dto.response.TestNameResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;

    /* Read All*/
    @Transactional(readOnly = true)
    public TestNameResponse findAllTests() {
        return TestNameResponse.from(testRepository.findAll());
    }
}