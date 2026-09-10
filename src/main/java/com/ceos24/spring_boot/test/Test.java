package com.ceos24.spring_boot.test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.Getter;

@Data
@Entity
@Getter
public class Test {

    @Id
    private Long id;
    private String name;
}