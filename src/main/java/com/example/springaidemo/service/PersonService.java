package com.example.springaidemo.service;

import com.example.springaidemo.model.Person;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PersonService {

    private final ChatClient chatClient;

    public PersonService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public List<Person> findAll() {
        return this.chatClient.prompt()
            .user("""
                Generate a list of 10 persons with random values.
                Each object should contain an auto-incremented id field starting from 1.
                The age value should be a random number between 18 and 99.
                Each person should have a unique name and email.
                """)
            .advisors(new SimpleLoggerAdvisor())
            .call()
            .entity(new ParameterizedTypeReference<List<Person>>() {});
    }
}
