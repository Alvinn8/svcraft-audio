package ca.bkaw.svcraftaudio;

/**
 * Utilities for generating ids.
 */
public class IdUtil {
    /**
     * Get a random server id.
     *
     * @return The server id.
     */
    public static String randomServerId() {
        return "s-" + random8Chars();
    }

    /**
     * Get a random user id.
     *
     * @return The user id.
     */
    public static String randomUserId() {
        return "u-" + random8Chars();
    }

    /**
     * Get a string of 8 random characters consisting of lowercase letters or
     * numbers only.
     *
     * @return The random string.
     */
    private static String random8Chars() {
        char[] chars = new char[8];
        for (int i = 0; i < chars.length; i++) {
            if (random(0, 26 + 10) > 26) {
                chars[i] = (char) random('0', '9');
            } else {
                chars[i] = (char) random('a', 'z');
            }
        }
        return new String(chars);
    }

    /**
     * Get a random number between min and max (both inclusive).
     *
     * @param min The minimum value (inclusive).
     * @param max The maximum value (inclusive).
     * @return The random value.
     */
    private static int random(int min, int max) {
        return (int) (Math.random() * (max - min + 1) + min);
    }
}
