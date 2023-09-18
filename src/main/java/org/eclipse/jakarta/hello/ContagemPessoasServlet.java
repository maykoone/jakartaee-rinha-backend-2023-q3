package org.eclipse.jakarta.hello;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/contagem-pessoas")
public class ContagemPessoasServlet extends HttpServlet {
    
    @Resource(lookup = "java:global/PessoaDataSource")
    DataSource dataSource;

    @Override
    protected void doGet(HttpServletRequest request,
        HttpServletResponse response) throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/plain");

        String sql = "SELECT COUNT(id) FROM pessoa;";
        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                try (PrintWriter out = response.getWriter()) {
                    out.write("" + rs.getLong(1));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
}
