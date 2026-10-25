package gowb;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** Carrega as classes do jar do jogo aplicando os ajustes do port (Patcher) antes de definir cada uma. */
final class GameLoader extends ClassLoader {
    final GameJar jar;

    GameLoader(GameJar jar) {
        super("jogo", GameLoader.class.getClassLoader());
        this.jar = jar;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] b = jar.entries.get(name.replace('.', '/') + ".class");
        if (b == null) throw new ClassNotFoundException(name);
        byte[] p = Patcher.transform(b, n -> jar.entries.get(n + ".class"));
        return defineClass(name, p, 0, p.length);
    }

    /** Recurso do jar do jogo ("/RP1", "minisoyo.pak"...). */
    InputStream resource(String name) {
        if (name.startsWith("/")) name = name.substring(1);
        byte[] b = jar.entries.get(name);
        return b == null ? null : new ByteArrayInputStream(b);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream in = resource(name);
        return in != null ? in : super.getResourceAsStream(name);
    }
}
