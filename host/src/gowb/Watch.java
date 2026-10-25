package gowb;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Depuração: mostra quando campos estáticos do jogo mudam ("classe.campo", nomes do jar original). */
final class Watch {
    private final List<String> specs;
    private final List<Field> fields = new ArrayList<>();
    private final List<Object> last = new ArrayList<>();

    Watch(List<String> specs) {
        this.specs = specs;
    }

    boolean isEmpty() {
        return specs.isEmpty();
    }

    void poll(long frame) {
        if (fields.isEmpty()) {
            for (String spec : specs) {
                int dot = spec.lastIndexOf('.');
                try {
                    for (Field f : Hooks.loader.loadClass(spec.substring(0, dot)).getDeclaredFields())
                        if (f.getName().equals(spec.substring(dot + 1)) && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                            f.setAccessible(true);
                            fields.add(f);
                            last.add(null);
                        }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalArgumentException(spec, e);
                }
            }
        }
        for (int i = 0; i < fields.size(); i++) {
            try {
                Object v = fields.get(i).get(null);
                if (v != null && !v.equals(last.get(i))) {
                    System.out.println("quadro " + frame + ": " + fields.get(i).getName() + " = " + v);
                    last.set(i, v);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
