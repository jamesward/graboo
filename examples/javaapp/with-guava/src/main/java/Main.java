import com.google.common.collect.ImmutableSet;

class Main {

    public static final ImmutableSet<String> COLOR_NAMES = ImmutableSet.of(
            "red",
            "orange",
            "yellow",
            "green",
            "blue",
            "purple");

    public static void main(String[] args) {
        System.out.println("size = " + COLOR_NAMES.size());
    }
}