package com.example.demo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MyApplicationTests {

    @Test
    void basic() {
        Assertions.assertEquals("asdf", "asdf");
    }

}
