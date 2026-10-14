package backend.hobbiebackend.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.web.client.RestTemplate;


@Configuration
public class HobbieConfigurationBeans {
    @Bean
    public PasswordEncoder createPasswordEncoder() {
        return new Pbkdf2PasswordEncoder();
    }

    @Bean
    public ModelMapper createModelMapper() {
        return new ModelMapper();
    }

    @Bean
    public RestTemplate createRestTemplate() {
        return new RestTemplate();
    }


}
