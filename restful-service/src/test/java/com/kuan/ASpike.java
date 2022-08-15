package com.kuan;


import com.tdd.di.ComponentRef;
import com.tdd.di.Config;
import com.tdd.di.Context;
import com.tdd.di.ContextConfig;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ASpike {

    Server server;


    @BeforeEach
    public void start() throws Exception {
        server = new Server(6666);

        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);

        ServletContextHandler handler = new ServletContextHandler(server, "/");

        TestApplication application = new TestApplication();
        handler.addServlet(new ServletHolder(new ResourceServlet(application, new TestProviders(application))), "/");

        server.setHandler(handler);
        server.start();
    }

    @AfterEach
    public void stop() throws Exception {
        server.stop();
    }

    @Test
    public void test() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder(new URI("http://localhost:6666/")).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response);
        System.out.println(response.body());

        assertEquals("prefixprefixqxk test in resource", response.body());
    }


    @Path("/test")
    static class TestResource {

        @Inject
        @Named("prefix")
        String prefix;

        public TestResource() {
        }

        @GET
        public String get() {
            return prefix + "qxk test in resource";
        }
    }


    static class TestApplication extends Application {

        private final Context context;

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(TestResource.class, StringMessageBodyWriter.class);
        }

        public Config getConfig() {
            return new Config() {
                @Named("prefix")
                public String name = "prefix";
            };
        }

        public Context getContext() {
            return context;
        }

        public TestApplication() {
            ContextConfig config = new ContextConfig();
            config.from(getConfig());

            List<Class<?>> writerClasses = this.getClasses().stream()
                    .filter(MessageBodyWriter.class::isAssignableFrom).toList();
            for (Class writerClass : writerClasses) {
                config.component(writerClass, writerClass);
            }


            List<Class<?>> rootResources = getClasses().stream().filter(c -> c.isAnnotationPresent(Path.class)).toList();
            for (Class rootResource : rootResources) {
                config.component(rootResource, rootResource);
            }

            context = config.getContext();
        }

        public ResourceContext createResourceContext(HttpServletRequest request, HttpServletResponse response) {
            return new ResourceContext() {
                @Override
                public <T> T getResource(Class<T> resourceClass) {
                    return null;
                }

                @Override
                public <T> T initResource(T resource) {
                    return resource;
                }
            };
        }

    }

    // Providers 里的信息，是从 Application 中获取到的。
    static class TestProviders implements Providers {
        private TestApplication application;
        private List<MessageBodyWriter> writers;

        public TestProviders(TestApplication application) {
            this.application = application;

            List<Class<?>> writerClasses = this.application.getClasses().stream()
                    .filter(MessageBodyWriter.class::isAssignableFrom).toList();

            writers = (List<MessageBodyWriter>) writerClasses.stream()
                    .map(c -> application.getContext().get(ComponentRef.of(c)).get())
                    .toList();
        }

        @Override
        public <T> MessageBodyReader<T> getMessageBodyReader(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public <T> MessageBodyWriter<T> getMessageBodyWriter(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return writers.stream()
                    .filter(w -> w.isWriteable(type, genericType, annotations, mediaType))
                    .findFirst().get();
        }

        @Override
        public <T extends Throwable> ExceptionMapper<T> getExceptionMapper(Class<T> type) {
            return null;
        }

        @Override
        public <T> ContextResolver<T> getContextResolver(Class<T> contextType, MediaType mediaType) {
            return null;
        }
    }


    static class StringMessageBodyWriter implements MessageBodyWriter<String> {

        @Inject
        @Named("prefix")
        String prefix;

        public StringMessageBodyWriter() {
        }

        @Override
        public boolean isWriteable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return type == String.class;
        }

        @Override
        public void writeTo(String s, Class type, Type genericType, Annotation[] annotations, MediaType mediaType,
                            MultivaluedMap httpHeaders, OutputStream entityStream) throws WebApplicationException {
            PrintWriter writer = new PrintWriter(entityStream);
            writer.write(prefix);
            writer.write(s);
            writer.flush();
        }
    }

    static class ResourceServlet extends HttpServlet {
        private TestApplication application;
        private Providers providers;

        public ResourceServlet(TestApplication application, Providers providers) {
            this.application = application;
            this.providers = providers;


        }


        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws NullPointerException, IOException {
            Stream<Class<?>> rootResources = application.getClasses().stream().filter(c -> c.isAnnotationPresent(Path.class));

            ResourceContext rc = application.createResourceContext(req, resp);


            Object result = dispatch(req, rootResources, rc);
            // 换成 dispatch
//            String result = new TestResource().get();


            MessageBodyWriter<Object> writer = (MessageBodyWriter<Object>) providers.getMessageBodyWriter(result.getClass(), null, null, null);
            writer.writeTo(result, null, null, null, null, null, resp.getOutputStream());
            // 换成 MessageBodyWriter
//            resp.getWriter().write(result.toString());
//            resp.getWriter().flush();
        }

        Object dispatch(HttpServletRequest req, Stream<Class<?>> rootResources, ResourceContext rc) {
            try {
                Class<?> rootClass = rootResources.findFirst().get();
                // >>>>>  应该用 di 去构造一个 component 出来。
//                Object rootResource = rootClass.getConstructor().newInstance();

                Object rootResource = rc.initResource(
                        application.getContext().get(ComponentRef.of(rootClass)).get()
                );

                Method method = Arrays.stream(rootClass.getMethods()).filter(m -> m.isAnnotationPresent(GET.class)).findFirst().get();
                return method.invoke(rootResource);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    }

}