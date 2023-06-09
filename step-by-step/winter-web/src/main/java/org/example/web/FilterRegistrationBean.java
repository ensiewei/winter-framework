package org.example.web;

import jakarta.servlet.Filter;

import java.util.List;

public abstract class FilterRegistrationBean {

    public abstract List<String> getUrlPatterns();

    /**
     * Get name by class name. Example:
     
     * ApiFilterRegistrationBean -> apiFilter
     
     * ApiFilterRegistration -> apiFilter
     
     * ApiFilterReg -> apiFilterReg
     */
    public String getName() {
        String name = getClass().getSimpleName();
        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        
        String FILTER_REGISTRATION_BEAN = "FilterRegistrationBean";
        if (name.endsWith(FILTER_REGISTRATION_BEAN) && name.length() > FILTER_REGISTRATION_BEAN.length()) {
            return name.substring(0, name.length() - FILTER_REGISTRATION_BEAN.length());
        }
        
        String FILTER_REGISTRATION = "FilterRegistration";
        if (name.endsWith(FILTER_REGISTRATION) && name.length() > FILTER_REGISTRATION.length()) {
            return name.substring(0, name.length() - FILTER_REGISTRATION.length());
        }
        return name;
    }
    
    public abstract Filter getFilter();

}
