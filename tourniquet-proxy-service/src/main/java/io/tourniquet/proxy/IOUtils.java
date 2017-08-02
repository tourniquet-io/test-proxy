package io.tourniquet.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.vertx.core.buffer.Buffer;

/**
 * Created on 27.06.2017.
 */
public class IOUtils {

    public static Buffer resourceAsBuffer(String resource) throws IOException {

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if(cl == null) {
            cl = IOUtils.class.getClassLoader();
        }

        final Buffer buf;
        try(InputStream is = cl.getResourceAsStream(resource);
            ByteArrayOutputStream out = new ByteArrayOutputStream()){

            int nRead;
            byte[] data = new byte[16384];

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                out.write(data, 0, nRead);
            }

            out.flush();
            buf = Buffer.buffer(out.toByteArray());
        }
        return buf;
    }
}
