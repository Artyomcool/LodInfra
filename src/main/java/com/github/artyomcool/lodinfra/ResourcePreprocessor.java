package com.github.artyomcool.lodinfra;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ResourcePreprocessor implements Closeable {

    private final Inflater inflater = new Inflater();
    private final Deflater deflater;

    public ResourcePreprocessor(int compressionLevel) {
        deflater = new Deflater(compressionLevel);
    }

    public Resource compressed(Resource resource) {
        if (resource.uncompressedSize != 0) {
            return resource;
        }

        deflater.reset();
        deflater.setInput(resource.data.asReadOnlyBuffer());
        deflater.finish();

        ByteBuffer temp = ByteBuffer.allocate(resource.data.capacity());
        int compressedSize = deflater.deflate(temp, Deflater.FULL_FLUSH);
        temp.flip();

        if (compressedSize >= resource.data.remaining()) {
            return resource;
        } else {
            ByteBuffer clone = ByteBuffer.allocate(temp.remaining());
            clone.put(temp);
            clone.flip();

            return new Resource(
                    resource.type,
                    resource.lang,
                    resource.name,
                    resource.sanitizedName,
                    resource.virtualPath + "#compressed",
                    clone,
                    resource.data.remaining()
            );
        }
    }

    public ByteBuffer uncompressed(Resource resource, ByteBuffer out) throws DataFormatException {
        out.clear();
        inflater.reset();
        inflater.setInput(resource.data.asReadOnlyBuffer());
        inflater.inflate(out);
        out.flip();

        return out;
    }



    @Override
    public void close() {
        inflater.end();
        deflater.end();
    }
}
