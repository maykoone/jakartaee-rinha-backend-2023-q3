package org.eclipse.jakarta.hello;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.JsonWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/pessoas/*")
public class PersonsServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PersonsServlet.class.getName());

    @Resource(lookup = "java:global/PersonsDataSource")
    DataSource dataSource;

    @Inject
    CacheManager manager;

    @Inject
    Cache cache;

    @PostConstruct
    void postConstruct() {
        LOG.info("---> Initialize PersonsServlet");
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        try {
            request.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");

            LOG.info("---> ContextPath: " + request.getContextPath()
                + "\n---> ServletPath: " + request.getServletPath()
                + "\n---> PathInfo   : " + request.getPathInfo());

            JsonReader jsonReader = Json.createReader(request.getReader());
            JsonObject person = jsonReader.readObject();
            jsonReader.close();

            String apelido = getJsonValueAsString("apelido", person);
            String nome = getJsonValueAsString("nome", person);
            var nascimento = Optional.ofNullable(getJsonValueAsString("nascimento", person)).map(LocalDate::parse);

            String[] stack = null;
            if (person.containsKey("stack")) {
                if (person.get("stack").getValueType() == ValueType.ARRAY) {
                    JsonArray ja = person.getJsonArray("stack");
                    stack = new String[ja.size()];
                    int index = 0;
                    for (JsonValue jsonValue : ja) {
                        if (!(jsonValue instanceof JsonString))
                            throw new IllegalStateException("stack deve ser um array de apenas strings");
                        stack[index++] = ((JsonString) jsonValue).getString();
                    }
                } else if (person.get("stack").getValueType() != ValueType.NULL) {
                    throw new IllegalStateException("stack deve ser um array de apenas strings");
                }
            }

            var personRecord = new Person(UUID.randomUUID(), apelido, nome, nascimento.orElse(null), stack);

            String sql = "INSERT INTO people (id, apelido, nome, nascimento, stack) VALUES (?, ?, ?, ?, ?);";
            try (
                    Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, personRecord.id(), Types.OTHER);
                stmt.setString(2, personRecord.apelido());
                stmt.setString(3, personRecord.nome());
                stmt.setObject(4, personRecord.nascimento(), Types.DATE);
                stmt.setString(5, personRecord.stack() != null ? String.join(",", personRecord.stack()) : null);
                int updatedRows = stmt.executeUpdate();
                LOG.info("Inserted " + updatedRows + " Person");
            } catch (Exception e) {
                LOG.severe(e.getMessage());
                response.setStatus(422);
                return;
            }

            LOG.info(personRecord.toString());

            response.setStatus(201);
            response.setHeader("Location", "/pessoas/" + personRecord.id());
        } catch (IllegalStateException e) {
            LOG.info(e.getMessage());
            response.setStatus(400);
        } catch (DateTimeException | IllegalArgumentException | NullPointerException e) {
            LOG.info(e.getMessage());
            response.setStatus(422);
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setBufferSize(8192);

        LOG.info("---> ContextPath: " + request.getContextPath()
            +"\n---> ServletPath: " + request.getServletPath()
            +"\n---> PathInfo   : " + request.getPathInfo()
            +"\n---> RequestURI : " + request.getRequestURI());

        if (!Objects.isNull(request.getPathInfo())) {
            String[] parts = request.getPathInfo().split("/");

            LOG.info("---> GET /persons/" + parts[1]);
            String id = parts[1];

            String sql = "SELECT id, apelido, nome, nascimento, stack FROM people where id = ?;";

            try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, UUID.fromString(id), Types.OTHER);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    JsonObjectBuilder model = createJsonObjectFromResultSet(rs);

                    try (JsonWriter jw = Json.createWriter(response.getWriter())) {
                        jw.writeObject(model.build());
                    }
                } else {
                    response.setStatus(404);
                }
            } catch (Exception e) {
                e.printStackTrace();
                response.setStatus(500);
            }
        } else if (!Objects.isNull(request.getParameter("t")) && !request.getParameter("t").equals("")) {
            String termo = request.getParameter("t");
            // String sql = "SELECT id, apelido, nome, nascimento, stack FROM people where apelido ilike ? or nome ilike ? or stack ilike ? LIMIT 50;";
            String sql = "SELECT id, apelido, nome, nascimento, stack FROM people where busca_trgm like ? LIMIT 50;";

            String jsonData = (String) cache.get(termo);
            if (jsonData != null) {
                LOG.info("---> From cache");
                response.getWriter().write(jsonData);
            } else {
                try (
                        Connection conn = dataSource.getConnection();
                        PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, "%" + termo.toLowerCase() + "%");
                    // stmt.setString(2, "%" + termo + "%");
                    // stmt.setString(3, "%" + termo + "%");
                    ResultSet rs = stmt.executeQuery();

                    JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

                    while (rs.next()) {
                        JsonObjectBuilder model = createJsonObjectFromResultSet(rs);

                        jsonArrayBuilder.add(model);
                    }
                    // try (JsonWriter jw = Json.createWriter(response.getWriter())) {
                    //     jw.writeArray(jsonArrayBuilder.build());
                    // }

                    StringWriter sw = new StringWriter();
                    try (JsonWriter jw = Json.createWriter(sw)) {
                        jw.writeArray(jsonArrayBuilder.build());
                        jsonData = sw.toString();
                        cache.put(termo, jsonData);
                        response.getWriter().write(jsonData);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    response.setStatus(500);
                }
            }
        } else {
            response.setStatus(400);
        }

    }

    private JsonObjectBuilder createJsonObjectFromResultSet(ResultSet rs) throws SQLException {
        UUID uuid = (UUID) rs.getObject(1);
        String apelido = rs.getString(2);
        String nome = rs.getString(3);
        LocalDate nascimento = rs.getObject(4, LocalDate.class);
        String stack = rs.getString(5);

        LOG.info("---> Retrieve Person"
         + "\n---> [id=" + uuid + ", apelido=" + apelido + ", nome=" + nome + ", nascimento="
                + nascimento + ", stack=" + stack + "]");

        JsonObjectBuilder model = Json.createObjectBuilder()
                .add("id", uuid.toString())
                .add("apelido", apelido)
                .add("nome", nome)
                .add("nascimento", nascimento.toString());

        if (stack == null)
            model.add("stack", JsonValue.NULL);
        else
            model.add("stack", Json.createArrayBuilder(Arrays.asList(stack.split(","))));
        return model;
    }

    public record Person(UUID id, String apelido, String nome, LocalDate nascimento, String[] stack) {
        public Person {
            Objects.requireNonNull(apelido, () -> "Apelido não pode ser nulo");
            Objects.requireNonNull(nome, () -> "Nome não pode ser nulo");
            Objects.requireNonNull(nascimento, () -> "Nascimento não pode ser nulo");

            if (apelido.length() > 32)
                throw new IllegalArgumentException("apelido pode ter até 32 caracteres");

            if (nome.length() > 100)
                throw new IllegalArgumentException("nome pode ter até 100 caracteres");

            if (stack != null) {
                for (String item : stack) {
                    if (item.length() > 32)
                        throw new IllegalArgumentException("uma stack pode ter até 32 caracteres");
                }
            }
        }
    }

    private String getJsonValueAsString(String key, JsonObject object) {
        if (object.containsKey(key)
                && (object.get(key).getValueType() == ValueType.STRING
                        || object.get(key).getValueType() == ValueType.NULL)) {
            return object.getString(key, null);
        } else {
            throw new IllegalStateException("o " + key + " deve ser uma string");
        }
    }

}
