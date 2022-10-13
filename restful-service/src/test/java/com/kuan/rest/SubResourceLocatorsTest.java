package com.kuan.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: qinxuekuan
 * @Date: 2022/10/8
 */
public class SubResourceLocatorsTest {

    @Test
    public void should_match_path_with_url() {
        SubResourceLocators locators = new SubResourceLocators(Messages.class.getMethods());

        ResourceRouter.SubResourceLocator locator = locators.findSubResource("/hello").get();

        assertEquals("Messages.hello", locator.toString());
    }

    @Test
    public void should_return_empty_if_not_match_url() {
        SubResourceLocators locators = new SubResourceLocators(Messages.class.getMethods());

        assertTrue(locators.findSubResource("/missing").isEmpty());
    }


    @Test
    public void should_call_locator_method_to_generate_sub_resource() {
        SubResourceLocators locators = new SubResourceLocators(Messages.class.getMethods());
        ResourceRouter.SubResourceLocator subResourceLocator = locators.findSubResource("/hello").get();
        UriInfoBuilder uriInfoBuilder = Mockito.mock(UriInfoBuilder.class);
        Mockito.when(uriInfoBuilder.getLastMatchedResource()).thenReturn(new Messages());

        ResourceRouter.Resource subResource = subResourceLocator.getSubResource(uriInfoBuilder);

        UriTemplate.MatchResult result = Mockito.mock(UriTemplate.MatchResult.class);
        Mockito.when(result.getRemaining()).thenReturn(null);
        ResourceRouter.ResourceMethod method = subResource.match(result, "GET",
                new String[]{MediaType.TEXT_PLAIN}, uriInfoBuilder).get();

        assertEquals("Message.content", method.toString());
    }


    @Path("/messages")
    static class Messages {

        @Path("/hello")
        public Message hello() {
            return new Message();
        }

    }

    static class Message {

        @GET
        public String content() {
            return "content";
        }

    }

}