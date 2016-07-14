package fileupload.scenarios;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;

import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;

@Component(
	immediate = true,
	property = {
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED + ":Boolean=true",
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "= ",
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/",
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/*",
		"equinox.http.multipartSupported=true",
		"equinox.http.whiteboard.servlet.multipart.location=/tmp",
		"equinox.http.whiteboard.servlet.multipart.maxFileSize:Long=2097152" // 2Mb limit
	},
	service = Servlet.class
)
public class FileUploadServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final String MULTIPART = "multipart/form-data";

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void service(
			HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException {

		String pathInfo = request.getPathInfo();

		if ((pathInfo != null) && pathInfo.startsWith("/i/")) {
			senfFile(request, pathInfo.substring(3));

			return;
		}

		response.setContentType("text/html");

		PrintWriter writer = response.getWriter();

		writer.println("<form action='.' enctype='multipart/form-data' method='post'>");
		writer.println("<input type='file' name='pic' accept='image/*'>");
		writer.println("<br/>");
		writer.println("<input type='submit'>");
		writer.println("</form>");

		if ((request.getContentType() != null) && request.getContentType().contains(MULTIPART)) {
			writer.println("<h3>Image</h3> ");

			Collection<Part> parts = request.getParts();

			request.getSession(true).setAttribute("parts", parts);

			parts.forEach(
				part -> {
					writer.println("<br/>Name: ");
					writer.println(part.getName());
					writer.println("<br/>Content Type: ");
					writer.println(part.getContentType());
					writer.println("<br/>Size: ");
					writer.println(part.getSize());
					writer.println("<br/>SubmittedFileName: ");
					writer.println(part.getSubmittedFileName());
					writer.println("<img src='./i/");
					writer.println(part.getSubmittedFileName());
					writer.println("' />");
				}
			);
		}
	}

	private void senfFile(HttpServletRequest request, String fileName) {
		AsyncContext asyncContext = request.startAsync();

		HttpSession session = request.getSession(true);

		asyncContext.start(() -> {
			try {
				@SuppressWarnings("unchecked")
				Collection<Part> parts = (Collection<Part>)session.getAttribute("parts");

				if (parts == null) {
					return;
				}

				HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
				WritableByteChannel outputChannel = Channels.newChannel(response.getOutputStream());

				parts.forEach(part -> {
					if (part.getSubmittedFileName().equals(fileName)) {
						response.setContentType(part.getContentType());
						response.setContentLengthLong(part.getSize());

						try {
							ReadableByteChannel inputChannel = Channels.newChannel(part.getInputStream());

							ByteBuffer buffer = ByteBuffer.allocate(2048);

							while (inputChannel.read(buffer) > 0) {
								buffer.flip();
								while (outputChannel.write(buffer) >0);
								buffer.clear();
							}

							inputChannel.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});

				outputChannel.close();
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
			}
			finally {
				asyncContext.complete();
			}
		});
	}

}