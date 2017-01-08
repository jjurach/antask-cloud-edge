package net.sl8;

import com.netflix.discovery.converters.Auto;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.ribbon.proxy.annotation.Hystrix;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

@EnableZuulProxy
@EnableBinding(Source.class)
@EnableDiscoveryClient
@EnableCircuitBreaker
@SpringBootApplication
public class TaskClientApplication {

    @Bean
    @LoadBalanced
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        SpringApplication.run(TaskClientApplication.class, args);
    }


    public Collection<String> fallback() {
        return new ArrayList<>();
    }

    @Autowired
    private Source outputChannelSource;

    @RequestMapping(method = RequestMethod.POST)
    public void write(@RequestBody Task t) {
        MessageChannel channel = this.outputChannelSource.output();
        channel.send(
                MessageBuilder.withPayload(t.getTaskName()).build()
        );
    }
}

@RestController
@RequestMapping("/tasks")
class TaskApiGatewayRestController {

    private final RestTemplate restTemplate;

    @Autowired
    public TaskApiGatewayRestController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @HystrixCommand(fallbackMethod = "fallback")
    @RequestMapping(method = RequestMethod.GET, value = "/names")
    public Collection<String> names() {
        return this.restTemplate.exchange("http://task-service/tasks",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Resources<Task>>() {
                })
                .getBody()
                .getContent()
                .stream()
                .map(Task::getTaskName)
                .collect(Collectors.toList());
    }

    @RequestMapping(method=RequestMethod.POST)
    public void write(@RequestBody Task t) {

    }
}

class Task {
    private String taskName;

    public String getTaskName() {
        return taskName;
    }
}