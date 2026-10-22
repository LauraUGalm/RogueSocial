package com.roguesocial.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.roguesocial.user.NotSignedInException;
import com.roguesocial.user.SignedIn;
import com.roguesocial.user.Sessions;

/**
 * Fills in any controller parameter of type {@link SignedIn} from the session, so a method that
 * takes one can only be reached signed in. Without a session it answers 401.
 */
@Configuration
public class SignedInResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == SignedIn.class;
    }

    @Override
    public SignedIn resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
            NativeWebRequest request, WebDataBinderFactory binders) {
        return Sessions.current(request.getNativeRequest(HttpServletRequest.class))
                .orElseThrow(NotSignedInException::new);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    /** For every controller, since any of them can take a {@link SignedIn}. */
    @RestControllerAdvice
    static class NotSignedInHandler {

        @ExceptionHandler(NotSignedInException.class)
        @ResponseStatus(HttpStatus.UNAUTHORIZED)
        public Map<String, String> notSignedIn(NotSignedInException e) {
            return Map.of("error", e.getMessage());
        }
    }
}
