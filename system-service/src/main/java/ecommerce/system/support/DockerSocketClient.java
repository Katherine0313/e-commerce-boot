package ecommerce.system.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 极简 Docker Engine API 客户端（Unix Domain Socket + HTTP/1.1）。
 * <p>
 * 只做只读查询（容器列表 / 容器 stats），不调用任何写接口。
 * 不引 docker-java：只用到两个 GET，手写 UDS 请求比引一整套客户端依赖更可控。
 */
@Slf4j
@Component
public class DockerSocketClient {

    private static final int BUFFER_SIZE = 8192;

    private final String socketPath;
    private final ObjectMapper objectMapper;

    public DockerSocketClient(@Value("${monitor.docker.socket:/var/run/docker.sock}") String socketPath,
                              ObjectMapper objectMapper) {
        this.socketPath = socketPath;
        this.objectMapper = objectMapper;
    }

    /** Docker socket 是否可用（本地 Windows 开发环境通常不可用） */
    public boolean isAvailable() {
        try {
            return Files.isReadable(Path.of(socketPath));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** GET /containers/json?all=1 */
    public JsonNode listContainers() throws IOException {
        return getJson("/containers/json?all=1");
    }

    /** GET /containers/{id}/stats?stream=false&one-shot=true */
    public JsonNode containerStats(String containerId) throws IOException {
        return getJson("/containers/" + containerId + "/stats?stream=false&one-shot=true");
    }

    public JsonNode getJson(String path) throws IOException {
        return objectMapper.readTree(httpGet(path));
    }

    private String httpGet(String path) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socketPath));
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: docker\r\n"
                    + "Accept: application/json\r\n"
                    + "Connection: close\r\n\r\n";
            channel.write(ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8)));
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                byte[] chunk = new byte[buffer.limit()];
                buffer.get(chunk);
                raw.write(chunk);
                buffer.clear();
            }
        }
        byte[] bytes = raw.toByteArray();
        int headerEnd = indexOf(bytes, new byte[]{'\r', '\n', '\r', '\n'});
        if (headerEnd < 0) {
            throw new IOException("Docker API 响应不完整");
        }
        String header = new String(bytes, 0, headerEnd, StandardCharsets.UTF_8);
        int status = parseStatus(header);
        byte[] body = Arrays.copyOfRange(bytes, headerEnd + 4, bytes.length);
        if (header.toLowerCase().contains("transfer-encoding: chunked")) {
            body = dechunk(body);
        }
        if (status < 200 || status >= 300) {
            throw new IOException("Docker API HTTP " + status + ": "
                    + new String(body, StandardCharsets.UTF_8));
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private int parseStatus(String header) throws IOException {
        String[] lines = header.split("\r\n");
        if (lines.length == 0) {
            throw new IOException("Docker API 响应头为空");
        }
        try {
            return Integer.parseInt(lines[0].split(" ")[1]);
        } catch (RuntimeException e) {
            throw new IOException("无法解析 Docker 响应状态: " + lines[0]);
        }
    }

    /** 解码 HTTP chunked 响应体（按字节处理，避免中文/多字节字符长度错位） */
    private byte[] dechunk(byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        while (pos < body.length) {
            int lineEnd = indexOf(body, pos, new byte[]{'\r', '\n'});
            if (lineEnd < 0) {
                break;
            }
            String sizeLine = new String(body, pos, lineEnd - pos, StandardCharsets.UTF_8).trim();
            int semicolon = sizeLine.indexOf(';');
            if (semicolon >= 0) {
                sizeLine = sizeLine.substring(0, semicolon);
            }
            int size;
            try {
                size = Integer.parseInt(sizeLine, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Docker chunked 响应格式错误: " + sizeLine);
            }
            pos = lineEnd + 2;
            if (size == 0) {
                break;
            }
            if (pos + size > body.length) {
                throw new IOException("Docker chunked 响应被截断");
            }
            out.write(body, pos, size);
            pos += size + 2;
        }
        return out.toByteArray();
    }

    private int indexOf(byte[] data, byte[] target) {
        return indexOf(data, 0, target);
    }

    private int indexOf(byte[] data, int from, byte[] target) {
        for (int i = from; i <= data.length - target.length; i++) {
            boolean matched = true;
            for (int j = 0; j < target.length; j++) {
                if (data[i + j] != target[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }
}
