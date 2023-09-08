package org.eclipse.jakarta.hello;

import java.io.IOException;
import java.io.Writer;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/greeting/*")
public class HelloServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
        HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setBufferSize(8192);

        // JsonGenerator gen = Json.createGenerator(response.getWriter());
        // gen.writeStartObject()
        //     .write("message", "Hello There!")
        //     .writeEnd();
        // gen.close();

        System.out.println("---> ContextPath: " + request.getContextPath());
        System.out.println("---> ServletPath: " + request.getServletPath());
        System.out.println("---> PathInfo   : " + request.getPathInfo());

        JsonObject model = Json.createObjectBuilder()
            .add("message", "Hello There!")
            .build();

        try (JsonWriter jw = Json.createWriter(response.getWriter())) {
            jw.writeObject(model);
        }
    }
    
}
