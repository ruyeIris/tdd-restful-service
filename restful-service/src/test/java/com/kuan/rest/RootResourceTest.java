package com.kuan.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: qxkk
 * @Date: 2022/9/26
 */
public class RootResourceTest {
    private Messages rootResource;
    private ResourceContext resourceContext;

    @BeforeEach
    public void before() {
        rootResource = new Messages();
        resourceContext = Mockito.mock(ResourceContext.class);
        when(resourceContext.getResource(Messages.class)).thenReturn(rootResource);
        when(resourceContext.getResource(MissingMessages.class)).thenReturn(new MissingMessages());
    }

    @Test
    public void should_get_uri_template_from_path_annotation() {
        ResourceRouter.Resource resource = new ResourceHandler(Messages.class);
        UriTemplate template = resource.getUriTemplate();
        assertTrue(template.match("/messages/hello").isPresent());
    }

    @ParameterizedTest(name = "{3}")
    @CsvSource(textBlock = """
            /messages/hello,       GET,        Messages.hello,          GET and URI match
            /messages/ah,          GET,        Messages.ah,             GET and URI match
            /messages/hello,       POST,       Messages.postHello,      POST and URI match
            /messages/hello,       PUT,        Messages.putHello,       PUT and URI match
            /messages/hello,       DELETE,     Messages.deleteHello,    DELETE and URI match
            /messages/hello,       PATCH,      Messages.patchHello,     PATCH and URI match
            /messages/hello,       HEAD,       Messages.headHello,      HEAD and URI match
            /messages/hello,       OPTIONS,    Messages.optionsHello,   OPTIONS and URI match
            /messages/topics/1234, GET,        Messages.topic1234,      GET with multiply choices
            /messages,             GET,        Messages.get,            GET with resource method without Path
            /messages/1/content,   GET,        Message.content,         Map to sub-resource method
            /messages/1/body/get,  GET,        MessageBody.get,         Map to sub-sub-resource method
            """)
    public void should_match_resource_method_in_root_resource(String path, String httpMethod, String resourceMethod,
                                                              String context) {
        StubUriInfoBuilder builder = new StubUriInfoBuilder();
        ResourceRouter.Resource resource = new ResourceHandler(Messages.class);
        UriTemplate.MatchResult result = resource.getUriTemplate().match(path).get();
        ResourceRouter.ResourceMethod method = resource.match(result, httpMethod,
                new String[]{MediaType.TEXT_PLAIN}, resourceContext, builder).get();
        assertEquals(resourceMethod, method.toString());
    }

    @Test
    public void should_match_resource_method_in_sub_resource() {
        ResourceRouter.Resource resource = new ResourceHandler(new Message(), Mockito.mock(UriTemplate.class));
        UriTemplate.MatchResult result = Mockito.mock(UriTemplate.MatchResult.class);
        when(result.getRemaining()).thenReturn("/content");

        assertTrue(resource.match(result, "GET", new String[]{MediaType.TEXT_PLAIN},
                resourceContext, Mockito.mock(UriInfoBuilder.class)).isPresent());
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource(textBlock = """
            /missing-messages/1,        GET,      URI not matched
            /missing-messages,          POST,     Http method not matched
            /missing-messages/sub/miss, POST,     No matched sub-resource method
            """)
    public void should_return_empty_if_not_matched(String uri, String httpMethod, String context) {
        UriInfoBuilder builder = new StubUriInfoBuilder();
        ResourceRouter.Resource resource = new ResourceHandler(MissingMessages.class);
        UriTemplate.MatchResult result = resource.getUriTemplate().match(uri).get();
        assertTrue(resource.match(result, httpMethod, new String[]{MediaType.TEXT_PLAIN},
                        resourceContext, builder)
                .isEmpty());
    }


    @Test
    public void should_add_last_match_resource_to_uri_info_builder() {
        StubUriInfoBuilder uriInfoBuilder = new StubUriInfoBuilder();

        ResourceHandler resource = new ResourceHandler(Messages.class);
        UriTemplate.MatchResult result = resource.getUriTemplate().match("/messages").get();

        resource.match(result, "GET", new String[]{MediaType.TEXT_PLAIN}, resourceContext, uriInfoBuilder);

        assertTrue(uriInfoBuilder.getLastMatchedResource() instanceof Messages);

    }

    @Test
    public void should_add_last_match_path_parameters_uri_info_builder() {
        StubUriInfoBuilder uriInfoBuilder = new StubUriInfoBuilder();

        ResourceHandler resource = new ResourceHandler(Messages.class);
        UriTemplate.MatchResult result = resource.getUriTemplate().match("/messages/1").get();

        resource.match(result, "GET", new String[]{MediaType.TEXT_PLAIN}, resourceContext, uriInfoBuilder);

        assertTrue(uriInfoBuilder.getLastMatchedResource() instanceof Message);

        assertEquals(List.of("1"), uriInfoBuilder.getPathParameters().get("id"));

        UriInfo uriInfo = uriInfoBuilder.createUriInfo();
        assertEquals(List.of("1"), uriInfo.getPathParameters().get("id"));
    }


    @Test
    public void should_throw_illegal_argument_exception_if_root_resource_not_have_path_annotation() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceHandler(Message.class));
    }

    @Test
    public void should_convert_get_resource_method_to_head_resource_method() {
        ResourceMethods resourceMethods = new ResourceMethods(Messages.class.getMethods());
        UriTemplate.MatchResult result = new PathUriTemplate("/messages").match("/messages/head").get();

        ResourceRouter.ResourceMethod method = resourceMethods.findResourceMethods(result.getRemaining(), "HEAD").get();

        assertInstanceOf(HeadResourceMethod.class, method);
    }

    @Test
    public void should_get_options_for_given_uri() {
        RuntimeDelegate delegate = mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        when(delegate.createResponseBuilder()).thenReturn(new StubResponseBuilder());

        ResourceContext context = mock(ResourceContext.class);
        UriInfoBuilder builder = mock(UriInfoBuilder.class);

        ResourceMethods resourceMethods = new ResourceMethods(Messages.class.getMethods());
        UriTemplate.MatchResult result = new PathUriTemplate("/messages").match("/messages/head").get();

        ResourceRouter.ResourceMethod method =
                resourceMethods.findResourceMethods(result.getRemaining(), HttpMethod.OPTIONS).get();

        GenericEntity<?> entity = method.call(context, builder);
        Response response = (Response) entity.getEntity();

        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        assertEquals(Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS), response.getAllowedMethods());
    }


    @Test
    public void should_not_include_head_in_options_if_given_uri_not_have_get_method() {
        RuntimeDelegate delegate = mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        when(delegate.createResponseBuilder()).thenReturn(new StubResponseBuilder());

        ResourceContext context = mock(ResourceContext.class);
        UriInfoBuilder builder = mock(UriInfoBuilder.class);

        ResourceMethods resourceMethods = new ResourceMethods(Messages.class.getMethods());
        UriTemplate.MatchResult result = new PathUriTemplate("/messages").match("/messages/no-head").get();

        ResourceRouter.ResourceMethod method =
                resourceMethods.findResourceMethods(result.getRemaining(), HttpMethod.OPTIONS).get();

        GenericEntity<?> entity = method.call(context, builder);
        Response response = (Response) entity.getEntity();

        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        assertEquals(Set.of(HttpMethod.POST, HttpMethod.OPTIONS), response.getAllowedMethods());
    }

    @Path("/missing-messages")
    static class MissingMessages {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return "/messages";
        }

        @Path("/sub")
        public Message getSub() {
            return new Message();
        }

    }

    @Path("/messages")
    static class Messages {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return "messages";
        }

        @GET
        @Path("/ah")
        @Produces(MediaType.TEXT_PLAIN)
        public String ah() {
            return "ah";
        }

        @GET
        @Path("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        public String hello() {
            return "hello";
        }

        @POST
        @Path("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        public String postHello() {
            return "postHello";
        }

        @PUT
        @Path("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        public String putHello() {
            return "Hello";
        }

        @DELETE
        @Path("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        public String deleteHello() {
            return "Hello";
        }

        @HEAD
        @Path("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        public String headHello() {
            return "Hello";
        }

        @OPTIONS
        @Path("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        public String optionsHello() {
            return "Hello";
        }

        @PATCH
        @Path("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        public String patchHello() {
            return "Hello";
        }

        @GET
        @Path("/topics/{id}")
        @Produces(MediaType.TEXT_PLAIN)
        public String topicId() {
            return "topicId";
        }

        @GET
        @Path("/topics/1234")
        @Produces(MediaType.TEXT_PLAIN)
        public String topic1234() {
            return "topic1234";
        }


        @Path("/{id}")
        public Message getById() {
            return new Message();
        }

        @GET
        @Path("/special")
        public String getSpecial() {
            return "special";
        }

        @GET
        @Path("/head")
        @Produces(MediaType.TEXT_PLAIN)
        public String getHead() {
            return "head";
        }

        @POST
        @Path("/no-head")
        @Produces(MediaType.TEXT_PLAIN)
        public void postNoHead() {
        }
    }


    static class Message {

        @GET
        @Path("/content")
        @Produces(MediaType.TEXT_PLAIN)
        public String content() {
            return "content";
        }

        @Path("/body")
        @Produces(MediaType.TEXT_PLAIN)
        public MessageBody body() {
            return new MessageBody();
        }

    }

    static class MessageBody {
        @GET
        @Path("/get")
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return "get";
        }
    }

}
