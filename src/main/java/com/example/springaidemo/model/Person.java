package com.example.springaidemo.model;

public record Person(
    Long id,
    String name,
    Integer age,
    String email
) {}
