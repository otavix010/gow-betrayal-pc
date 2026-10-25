package gowb;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Ajustes aplicados no bytecode original do jogo, na hora de carregar (nada do jogo fica no app).
 *
 * <ul>
 *   <li>todas as classes: Class.getResourceAsStream lê do jar do jogo (o jogo pede recursos por String.class,
 *       que no Java moderno não enxerga o jar); relógio e sleep passam pelo Platform.
 *   <li>motor (subclasse de GameCanvas): texto por índice passa pela tradução; a busca de glifo troca letra
 *       acentuada pela letra base; o desenho do glifo passa pelo Hooks, que desenha o acento.
 * </ul>
 *
 * As classes são CLDC (versão 45), verificadas sem StackMapTable, então não é preciso recalcular frames.
 */
final class Patcher {
    private Patcher() {}

    private static final String HOOKS = "gowb/Hooks", PLATFORM = "gowb/Platform";
    private static final String GRAPHICS = "Ljavax/microedition/lcdui/Graphics;";

    private static final String GAME_CANVAS = "javax/microedition/lcdui/game/GameCanvas";

    /** @param classe bytes de outra classe do jar pelo nome interno (para saber a hierarquia), ou null */
    static byte[] transform(byte[] original, java.util.function.Function<String, byte[]> classe) {
        ClassReader cr = new ClassReader(original);
        String sup = cr.getSuperName();
        boolean motor = GAME_CANVAS.equals(sup);
        byte[] supBytes = motor ? null : classe.apply(sup);
        boolean jogo = supBytes != null && GAME_CANVAS.equals(new ClassReader(supBytes).getSuperName());
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
                MethodVisitor mv = new Redirect(super.visitMethod(access, name, desc, sig, exc));
                if (Platform.larguraJogo != 240 && (motor || jogo)) {
                    mv = new LarguraHook(mv, jogo && METADE.contains(name + desc));
                    if (jogo && desc.equals("([I[BIIIII)V")) mv = new CaixaTextoHook(mv);
                }
                if (jogo && name.equals("<init>")) return new QuadroHook(mv, sup);
                if (!motor || (access & Opcodes.ACC_STATIC) != 0) return mv;
                if (name.equals("run") && desc.equals("()V")) return new RelogioHook(mv);
                if (desc.equals("(I)Ljava/lang/String;")) return new TextoHook(mv);
                if (desc.startsWith("(C)L") && !desc.startsWith("(C)Ljava/")) return new GlifoHook(mv);
                if (desc.equals("(" + GRAPHICS + "[CIIIII)V")) return new DesenhoHook(mv);
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }

    /**
     * Métodos do jogo (build v0.1.95) em que 120 é a metade da largura da tela: câmera (j, l, p), textos da barra de
     * baixo e centralizações (a(Graphics,int,int), c(Graphics), u(Graphics)). Nos outros, 120 é id de animação/tempo.
     */
    private static final java.util.Set<String> METADE = java.util.Set.of("j()V", "l()V", "p()V", "a(" + GRAPHICS + "II)V",
            "u(" + GRAPHICS + ")V", "w(" + GRAPHICS + ")V");

    /**
     * Widescreen: a caixa de texto do jogo (layout(linhas, texto, x, y, largura, altura, ...)) recebe posições fixas
     * de 240 px. Diálogos das fases (y = 0) ficam mais largos; as outras caixas (pergunta do áudio, história) vão para
     * o centro da tela.
     */
    private static final class CaixaTextoHook extends MethodVisitor {
        CaixaTextoHook(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            Label centraliza = new Label(), fim = new Label();
            super.visitVarInsn(Opcodes.ILOAD, 4); // y
            super.visitJumpInsn(Opcodes.IFNE, centraliza);
            // y == 0: largura += larguraJogo - 240
            super.visitVarInsn(Opcodes.ILOAD, 5);
            sobra();
            super.visitInsn(Opcodes.IADD);
            super.visitVarInsn(Opcodes.ISTORE, 5);
            super.visitJumpInsn(Opcodes.GOTO, fim);
            // senão: x += (larguraJogo - 240) / 2
            super.visitLabel(centraliza);
            super.visitVarInsn(Opcodes.ILOAD, 3);
            sobra();
            super.visitInsn(Opcodes.ICONST_1);
            super.visitInsn(Opcodes.ISHR);
            super.visitInsn(Opcodes.IADD);
            super.visitVarInsn(Opcodes.ISTORE, 3);
            super.visitLabel(fim);
        }

        private void sobra() {
            super.visitFieldInsn(Opcodes.GETSTATIC, PLATFORM, "larguraJogo", "I");
            super.visitLdcInsn(240); // ldc: o LarguraHook não troca (sipush 240 viraria a própria largura)
            super.visitInsn(Opcodes.ISUB);
        }
    }

    /**
     * Widescreen: a largura de tela 240 (e a metade, 120, onde é centro de tela) vira Platform.larguraJogo.
     * Exceção: quando o número vai direto para a busca de texto ou para as legendas das soft keys, ele é o índice de
     * uma frase (240 = "Skip", 120 = "Play"), não uma largura.
     */
    private static final class LarguraHook extends MethodVisitor {
        private final boolean metade;
        private int pendente; // 0 = nada; senão a constante adiada (240 ou 120)

        LarguraHook(MethodVisitor mv, boolean metade) {
            super(Opcodes.ASM9, mv);
            this.metade = metade;
        }

        private void solta(boolean largura) {
            if (pendente == 0) return;
            int v = pendente;
            pendente = 0;
            if (!largura) {
                super.visitIntInsn(v == 240 ? Opcodes.SIPUSH : Opcodes.BIPUSH, v);
                return;
            }
            super.visitFieldInsn(Opcodes.GETSTATIC, PLATFORM, "larguraJogo", "I");
            if (v == 120) {
                super.visitInsn(Opcodes.ICONST_1);
                super.visitInsn(Opcodes.ISHR);
            }
        }

        @Override
        public void visitIntInsn(int op, int operand) {
            solta(true);
            if ((op == Opcodes.SIPUSH && operand == 240) || (metade && op == Opcodes.BIPUSH && operand == 120)) pendente = operand;
            else super.visitIntInsn(op, operand);
        }

        @Override
        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            boolean indice = desc.equals("(I)Ljava/lang/String;") || desc.equals("(" + GRAPHICS + "II)V");
            solta(!indice);
            super.visitMethodInsn(op, owner, name, desc, itf);
        }

        @Override
        public void visitInsn(int op) {
            solta(true);
            super.visitInsn(op);
        }

        @Override
        public void visitVarInsn(int op, int v) {
            solta(true);
            super.visitVarInsn(op, v);
        }

        @Override
        public void visitFieldInsn(int op, String owner, String name, String desc) {
            solta(true);
            super.visitFieldInsn(op, owner, name, desc);
        }

        @Override
        public void visitJumpInsn(int op, Label l) {
            solta(true);
            super.visitJumpInsn(op, l);
        }

        @Override
        public void visitLabel(Label l) {
            solta(true);
            super.visitLabel(l);
        }

        @Override
        public void visitTypeInsn(int op, String type) {
            solta(true);
            super.visitTypeInsn(op, type);
        }

        @Override
        public void visitLdcInsn(Object v) {
            solta(true);
            super.visitLdcInsn(v);
        }

        @Override
        public void visitIincInsn(int v, int inc) {
            solta(true);
            super.visitIincInsn(v, inc);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            solta(true);
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            solta(true);
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            solta(true);
            super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public void visitMaxs(int s, int l) {
            solta(true);
            super.visitMaxs(s, l);
        }
    }

    /** Recursos, relógio e sleep. */
    private static final class Redirect extends MethodVisitor {
        Redirect(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /** Textos fixos no código (fora da tabela de frases), traduzidos junto com o resto. */
        @Override
        public void visitLdcInsn(Object v) {
            if (v instanceof String s && !Main.idioma.equals("en")) {
                if (s.equals(" ON")) v = " LIGADO";
                else if (s.equals(" OFF")) v = " DESLIGADO";
            }
            super.visitLdcInsn(v);
        }

        @Override
        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class") && name.equals("getResourceAsStream")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "resource", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;", false);
            } else if (op == Opcodes.INVOKESTATIC && owner.equals("java/lang/System") && name.equals("currentTimeMillis")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PLATFORM, "now", "()J", false);
            } else if (op == Opcodes.INVOKESTATIC && owner.equals("java/lang/Thread") && name.equals("sleep") && desc.equals("(J)V")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PLATFORM, "sleep", "(J)V", false);
            } else {
                super.visitMethodInsn(op, owner, name, desc, itf);
            }
        }
    }

    /** No início de String g(int): se houver tradução para o índice, devolve ela. */
    private static final class TextoHook extends MethodVisitor {
        TextoHook(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            Label original = new Label();
            super.visitVarInsn(Opcodes.ILOAD, 1);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "texto", "(I)Ljava/lang/String;", false);
            super.visitInsn(Opcodes.DUP);
            super.visitJumpInsn(Opcodes.IFNULL, original);
            super.visitInsn(Opcodes.ARETURN);
            super.visitLabel(original);
            super.visitInsn(Opcodes.POP);
        }
    }

    /** No início de d b(char): troca a letra acentuada pela base (e lembra o acento para o desenho). */
    private static final class GlifoHook extends MethodVisitor {
        GlifoHook(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            super.visitVarInsn(Opcodes.ILOAD, 1);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "glifo", "(C)C", false);
            super.visitVarInsn(Opcodes.ISTORE, 1);
        }
    }

    /**
     * No construtor do jogo (subclasse do motor): o tempo mínimo de quadro do motor (bipush 40 + putfield int do
     * motor = 25 fps) passa a vir de Platform.frameMs. O motor usa o tempo real do quadro na lógica, então o jogo
     * fica mais fluido sem ficar mais rápido.
     */
    private static final class QuadroHook extends MethodVisitor {
        private final String motor;
        private boolean pendente, feito;

        QuadroHook(MethodVisitor mv, String motor) {
            super(Opcodes.ASM9, mv);
            this.motor = motor;
        }

        private void solta() {
            if (pendente) super.visitIntInsn(Opcodes.BIPUSH, 40);
            pendente = false;
        }

        @Override
        public void visitIntInsn(int op, int operand) {
            solta();
            if (!feito && op == Opcodes.BIPUSH && operand == 40) pendente = true;
            else super.visitIntInsn(op, operand);
        }

        @Override
        public void visitFieldInsn(int op, String owner, String name, String desc) {
            if (pendente && op == Opcodes.PUTFIELD && owner.equals(motor) && desc.equals("I")) {
                pendente = false;
                feito = true;
                super.visitFieldInsn(Opcodes.GETSTATIC, PLATFORM, "frameMs", "I");
            }
            solta();
            super.visitFieldInsn(op, owner, name, desc);
        }

        @Override
        public void visitInsn(int op) {
            solta();
            super.visitInsn(op);
        }

        @Override
        public void visitVarInsn(int op, int v) {
            solta();
            super.visitVarInsn(op, v);
        }

        @Override
        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            solta();
            super.visitMethodInsn(op, owner, name, desc, itf);
        }

        @Override
        public void visitTypeInsn(int op, String type) {
            solta();
            super.visitTypeInsn(op, type);
        }

        @Override
        public void visitLdcInsn(Object v) {
            solta();
            super.visitLdcInsn(v);
        }

        @Override
        public void visitJumpInsn(int op, Label l) {
            solta();
            super.visitJumpInsn(op, l);
        }

        @Override
        public void visitLabel(Label l) {
            solta();
            super.visitLabel(l);
        }
    }

    /**
     * Bug do motor original: o laço guarda como início do quadro o horário de ANTES do sleep, e o quadro seguinte
     * conta esse sleep de novo. Em celular lento não aparecia; em máquina rápida o jogo anda quase 2x mais rápido.
     * Depois de cada sleep do run(), atualiza o horário do quadro (variável local 1, long) para o horário atual.
     */
    private static final class RelogioHook extends MethodVisitor {
        RelogioHook(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            if (op == Opcodes.INVOKESTATIC && owner.equals("java/lang/Thread") && name.equals("sleep") && desc.equals("(J)V")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PLATFORM, "sleepQuadro", "(J)V", false);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PLATFORM, "now", "()J", false);
                super.visitVarInsn(Opcodes.LSTORE, 1);
            } else {
                super.visitMethodInsn(op, owner, name, desc, itf);
            }
        }
    }

    /** Em a(Graphics, char[], ...): o desenho de cada glifo passa pelo Hooks. */
    private static final class DesenhoHook extends MethodVisitor {
        DesenhoHook(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            if (op == Opcodes.INVOKEVIRTUAL && desc.equals("(" + GRAPHICS + "III)V") && !owner.startsWith("javax/")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "desenhaGlifo", "(Ljava/lang/Object;" + GRAPHICS + "III)V", false);
            } else {
                super.visitMethodInsn(op, owner, name, desc, itf);
            }
        }
    }
}
