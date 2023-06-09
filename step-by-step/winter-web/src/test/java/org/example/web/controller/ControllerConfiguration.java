package org.example.web.controller;

import org.example.annotation.Configuration;
import org.example.annotation.Import;
import org.example.web.WebMvcConfiguration;

@Configuration
@Import(WebMvcConfiguration.class)
public class ControllerConfiguration {

}
