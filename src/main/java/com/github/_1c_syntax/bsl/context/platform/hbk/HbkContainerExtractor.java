package com.github._1c_syntax.bsl.context.platform.hbk;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HbkContainerExtractor {
    private static final String PACK_BLOCK_NAME = "PackBlock";
    private static final String FILE_STORAGE_NAME = "FileStorage";

    private static final int BYTES_BY_FILE_INFOS = 12; // int * 4

    private static final Set<String> ACCEPT_FILE_NAMES = Set.of(PACK_BLOCK_NAME, FILE_STORAGE_NAME);

    public static Map<String, byte[]> extractHbkEntities(Path pathToHbk) {
        if (!pathToHbk.toFile().exists()) {
            throw new IllegalArgumentException("Hbk-file not exists");
        }

        Map<String, byte[]> entities = new HashMap<>();

        try (var stream = new FileInputStream(pathToHbk.toFile())) {
            var channel = stream.getChannel();
            var buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            skipBlock(buffer, 16); // int * 4

            skipBlock(buffer, 2); // short
            var payloadSize = getLongString(buffer);
            var blockSize = getLongString(buffer);
            skipBlock(buffer, 11); // long + byte + short

            int position = buffer.position();

            var fileInfos = new byte[payloadSize];
            buffer.get(fileInfos);

            buffer.position(position + blockSize);

            var remainingBuffer = ByteBuffer.wrap(fileInfos).order(ByteOrder.LITTLE_ENDIAN);
            var count = remainingBuffer.capacity() / BYTES_BY_FILE_INFOS;

            // 559
            for (int i = 0; i < count; ++i) {
                var headerAddress = remainingBuffer.getInt();
                var bodyAddress = remainingBuffer.getInt();
                int reserved = remainingBuffer.getInt();
                if (reserved != Integer.MAX_VALUE) {
                    throw new RuntimeException();
                }

                var name = getHbkFileName(buffer, headerAddress);

                if (ACCEPT_FILE_NAMES.contains(name)) {
                    var body = getHbkFileBody(buffer, bodyAddress);

                    entities.put(name, body);

                    if (entities.size() == ACCEPT_FILE_NAMES.size()) {
                        break;
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return entities;
    }

    private static String getHbkFileName(ByteBuffer buffer, int headerAddress) {
        buffer.position(headerAddress);

        skipBlock(buffer, 2);
        var payloadSize = getLongString(buffer); // + 8 + 1
        skipBlock(buffer, 40); // 8 + 1 + 8 + 1 + 2 + 8 + 8 + 4

        byte[] stringArray = new byte[payloadSize - 24]; // int * 6, которые пропускаем
        buffer.get(stringArray);

        return new String(stringArray, StandardCharsets.UTF_16LE);
    }

    private static byte[] getHbkFileBody(ByteBuffer outBuffer, int bodyAddress) {
        outBuffer.position(bodyAddress);

        skipBlock(outBuffer, 2);
        var payloadSize = getLongString(outBuffer);

        skipBlock(outBuffer, 20); // long * 2 + int * 2 + short

        // получим буффер, позици и размер для чтения тела
        var rawData = new byte[payloadSize]; // blockSize
        outBuffer.get(rawData);

        return rawData;
    }

    private static int getLongString(ByteBuffer buffer) {
        byte[] stringBuffer = new byte[8];
        buffer.get(stringBuffer);
        buffer.get();
        return (int) Long.parseLong(new String(stringBuffer), 16);
    }

    private static void skipBlock(ByteBuffer buffer, int size) {
        buffer.position(buffer.position() + size);
    }

}
