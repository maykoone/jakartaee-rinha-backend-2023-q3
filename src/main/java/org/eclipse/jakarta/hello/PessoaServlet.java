package org.eclipse.jakarta.hello;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

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

@WebServlet(value="/pessoas/*", loadOnStartup = 0)
public class PessoaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PessoaServlet.class.getName());

    @Resource(lookup = "java:global/PessoaDataSource")
    DataSource dataSource;

    @Inject
    CacheManager manager;

    // @Inject
    // javax.cache.Cache<String, Object> cache;
    Map<String, Object> cache;

    @PostConstruct
    void postConstruct() {
        LOG.info("---> Initialize PessoaServlet");

        cache = new HashMap<>();
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        try {
            prepareResponse(response);
            logRequest(request);

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

            var personRecord = new Pessoa(UUID.randomUUID(), apelido, nome, nascimento.orElse(null), stack);

            if (apelidoJaCriado(apelido)) {
                LOG.severe("Apelido ja criado");
                response.setStatus(422);
                return;
            }

            String sql = "INSERT INTO pessoa (id, apelido, nome, nascimento, stack) VALUES (?, ?, ?, ?, ?);";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, personRecord.id(), Types.OTHER);
                stmt.setString(2, personRecord.apelido());
                stmt.setString(3, personRecord.nome());
                stmt.setObject(4, personRecord.nascimento(), Types.DATE);
                stmt.setString(5, personRecord.stack() != null ? String.join(",", personRecord.stack()) : null);
                int updatedRows = stmt.executeUpdate();
                LOG.info("Inserted " + updatedRows + " Person");
                incluirApelidoCache(apelido);
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

    private void incluirApelidoCache(String apelido) {
        if (cache != null)
            cache.put("post"+ apelido, 1);
    }

    private boolean apelidoJaCriado(String apelido) {
        return cache != null && cache.containsKey("post"+apelido);
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        prepareResponse(response);
        logRequest(request);

        if (!Objects.isNull(request.getPathInfo())) {
            buscarPessoaPorId(request, response);
        } else if (!Objects.isNull(request.getParameter("t")) && !request.getParameter("t").equals("")) {
            buscarPessoasPorTermo(request, response);
        } else {
            response.setStatus(400);
        }

    }

    private void buscarPessoasPorTermo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String termo = request.getParameter("t");
        // String sql = "SELECT id, apelido, nome, nascimento, stack FROM pessoa where apelido ilike ? or nome ilike ? or stack ilike ? LIMIT 50;";
        String sql = "SELECT id, apelido, nome, nascimento, stack FROM pessoa where busca_trgm like ? LIMIT 50;";

        String jsonData = (String) (cache != null ? cache.get(termo) : null);
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

                if (cache == null) {
                    try (JsonWriter jw = Json.createWriter(response.getWriter())) {
                        jw.writeArray(jsonArrayBuilder.build());
                    }

                } else {
                    StringWriter sw = new StringWriter();
                    try (JsonWriter jw = Json.createWriter(sw)) {
                        jw.writeArray(jsonArrayBuilder.build());
                        jsonData = sw.toString();
                        cache.put(termo, jsonData);
                        response.getWriter().write(jsonData);
                    }
                }
            } catch (Exception e) {
                LOG.severe(e.getMessage());
                response.setStatus(500);
            }
        }
    }

    private void buscarPessoaPorId(HttpServletRequest request, HttpServletResponse response) {
        String[] parts = request.getPathInfo().split("/");

        LOG.info("---> GET /persons/" + parts[1]);
        String id = parts[1];

        String sql = "SELECT id, apelido, nome, nascimento, stack FROM pessoa where id = ?;";

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
    }

    private void logRequest(HttpServletRequest request) {
        LOG.info("---> ContextPath: " + request.getContextPath()
            +"\n---> ServletPath: " + request.getServletPath()
            +"\n---> PathInfo   : " + request.getPathInfo()
            +"\n---> RequestURI : " + request.getRequestURI());
    }

    private void prepareResponse(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setBufferSize(8192);
    }

    private JsonObjectBuilder createJsonObjectFromResultSet(ResultSet rs) throws SQLException {
        UUID uuid = (UUID) rs.getObject(1);
        String apelido = rs.getString(2);
        String nome = rs.getString(3);
        LocalDate nascimento = rs.getObject(4, LocalDate.class);
        String stack = rs.getString(5);

        LOG.info("---> Pessoa"
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
