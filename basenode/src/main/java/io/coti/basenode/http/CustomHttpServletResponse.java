package io.coti.basenode.http;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;

public class CustomHttpServletResponse extends HttpServletResponseWrapper {

    public CustomHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    public void printResponse(BaseResponse response, int status) throws IOException {
        PrintWriter writer = this.getWriter();
        this.setStatus(HttpStatus.SC_UNAUTHORIZED);
        this.setContentType("application/json");
        this.setCharacterEncoding("UTF-8");
        writer.print(new Gson().toJson(response));
        writer.flush();
    }

}