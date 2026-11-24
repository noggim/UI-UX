package com.yongsa;

import com.yongsa.config.Story;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryTest {
    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        return Files.isDirectory(p.resolve("assets")) ? p : p.getParent();
    }

    @Test
    void openingAndEndingHaveThreePicturesEach() throws Exception {
        Story s = Story.parse(Files.readString(root().resolve("assets/data/story.json")));
        assertEquals(3, s.opening.size());
        assertEquals(3, s.ending.size());
        for (Story.Page p : s.opening) assertTrue(Files.exists(root().resolve("assets/story/" + p.image)), p.image);
        for (Story.Page p : s.ending) assertTrue(Files.exists(root().resolve("assets/story/" + p.image)), p.image);
    }
}
