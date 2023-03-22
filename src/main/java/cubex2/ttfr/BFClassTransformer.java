package cubex2.ttfr;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ListIterator;

public class BFClassTransformer implements IClassTransformer, Opcodes
{
    private static final boolean developerEnvironment = (boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");

    private enum MethodName {
        FontRenderer_posX("posX", "field_78295_j"),
        ;
        private final String deobfName;
        private final String seargeName;

        MethodName(String deobfName, String seargeName) {
            this.deobfName = deobfName;
            this.seargeName = seargeName;
        }

        public String getName() {
            return developerEnvironment ? deobfName : seargeName;
        }
    }

    private static final String FIELD_ENABLED = "bf_enabled";

    @Override
    public byte[] transform(String s, String s1, byte[] bytes)
    {
        if (s.equals("net.minecraft.client.gui.FontRenderer"))
        {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(bytes);
            classReader.accept(classNode, 0);

            transform(classNode);

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }
        return bytes;
    }

    private void transform(ClassNode classNode)
    {
        System.out.println("Transforming class " + classNode.name);

        for (MethodNode method : classNode.methods)
        {
            System.out.println("- " + method.name + " " + method.desc);
        }

        String resourceLocation = "net/minecraft/util/ResourceLocation";
        String posX = MethodName.FontRenderer_posX.getName();

        classNode.interfaces.add("cubex2/ttfr/IBFFontRenderer");
        classNode.fields.add(new FieldNode(ACC_PUBLIC, "stringCache", "Lcubex2/ttfr/StringRenderer;", null, null));
        classNode.fields.add(new FieldNode(ACC_PUBLIC, "dropShadowEnabled", "Z", null, true));
        classNode.fields.add(new FieldNode(ACC_PUBLIC, FIELD_ENABLED, "Z", null, true));

        addGetterAndSetter(classNode, "setStringRenderer", "getStringRenderer", "stringCache", "Lcubex2/ttfr/StringRenderer;", ARETURN);
        addGetterAndSetter(classNode, "setDropShadowEnabled", "isDropShadowEnabled", "dropShadowEnabled", "Z", IRETURN);
        addGetterAndSetter(classNode, "setEnabled", "isEnabled", FIELD_ENABLED, "Z", IRETURN);

        MethodNode m = findMethod(classNode, null, "<init>");

        ListIterator<AbstractInsnNode> iterator = m.instructions.iterator();
        while (iterator.hasNext())
        {
            AbstractInsnNode node = iterator.next();
            if (node instanceof MethodInsnNode)
            {
                MethodInsnNode methodNode = (MethodInsnNode) node;
                if (hasName(methodNode, "readGlyphSizes", "func_98306_d")
                    && methodNode.desc.equals("()V"))
                {
                    InsnList toInject = new InsnList();

                    toInject.add(new VarInsnNode(ALOAD, 2));
                    toInject.add(new MethodInsnNode(INVOKESTATIC, "cubex2/ttfr/FontRendererCallback", "constructor",
                                                    "(Lcubex2/ttfr/IBFFontRenderer;L" + resourceLocation + ";)V", false));
                    toInject.add(new VarInsnNode(ALOAD, 0));
                    m.instructions.insertBefore(methodNode, toInject);

                    break;
                }
            }
        }

        System.out.println(classNode.name);
        for (MethodNode method : classNode.methods)
        {
            System.out.println(method.name + " " + method.desc);
        }
        m = findMethod(classNode, "(Ljava/lang/String;FFIZ)I", "drawString", "func_175065_a");
        System.out.println(m);
        iterator = m.instructions.iterator();
        while (iterator.hasNext())
        {
            AbstractInsnNode node = iterator.next();
            if (node instanceof VarInsnNode)
            {
                VarInsnNode varNode = (VarInsnNode) node;
                if (varNode.getOpcode() == ILOAD && varNode.var == 5)
                {
                    JumpInsnNode jumpNode = (JumpInsnNode) iterator.next();

                    InsnList toInject = new InsnList();
                    toInject.add(new VarInsnNode(ALOAD, 0));
                    toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "dropShadowEnabled", "Z"));
                    toInject.add(new JumpInsnNode(IFEQ, jumpNode.label));
                    m.instructions.insert(jumpNode, toInject);

                    break;
                }
            }
        }

        {
            m = findMethod(classNode, "(Ljava/lang/String;)Ljava/lang/String;", "bidiReorder", "func_147647_b");

            InsnList toInject = new InsnList();
            toInject.add(new VarInsnNode(ALOAD, 0));
            toInject.add(new VarInsnNode(ALOAD, 1));
            toInject.add(new MethodInsnNode(INVOKESTATIC, "cubex2/ttfr/FontRendererCallback", "bidiReorder",
                                            "(Lcubex2/ttfr/IBFFontRenderer;Ljava/lang/String;)Ljava/lang/String;", false));
            toInject.add(new InsnNode(ARETURN));
            m.instructions.insertBefore(m.instructions.getFirst(), toInject);
        }

        m = findMethod(classNode, "(Ljava/lang/String;FFIZ)I", "renderString", "func_180455_b");
        iterator = m.instructions.iterator();
        while (iterator.hasNext())
        {
            AbstractInsnNode node = iterator.next();
            if (node instanceof MethodInsnNode)
            {
                MethodInsnNode methodNode = (MethodInsnNode) node;
                if (hasName(methodNode, "renderStringAtPos", "func_78255_a"))
                {
                    // Remove call
                    AbstractInsnNode firstNode = methodNode.getPrevious().getPrevious().getPrevious();
                    LabelNode end = (LabelNode) methodNode.getNext();

                    Label label = new Label();
                    LabelNode labelNode = new LabelNode(label);
                    m.instructions.insertBefore(firstNode, labelNode);


                    InsnList toInject = new InsnList();
                    toInject.add(new FieldInsnNode(GETSTATIC, "cubex2/ttfr/FontRendererCallback", "betterFontsEnabled", "Z"));
                    toInject.add(new JumpInsnNode(IFEQ, labelNode));
                    toInject.add(new VarInsnNode(ALOAD, 0));
                    toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "stringCache", "Lcubex2/ttfr/StringRenderer;"));
                    toInject.add(new JumpInsnNode(IFNULL, labelNode));

                    toInject.add(new VarInsnNode(ALOAD, 0));
                    toInject.add(new InsnNode(DUP));
                    toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "stringCache", "Lcubex2/ttfr/StringRenderer;"));
                    toInject.add(new VarInsnNode(ALOAD, 1));
                    toInject.add(new VarInsnNode(FLOAD, 2));
                    toInject.add(new VarInsnNode(FLOAD, 3));
                    toInject.add(new VarInsnNode(ILOAD, 4));
                    toInject.add(new VarInsnNode(ILOAD, 5));
                    toInject.add(new MethodInsnNode(INVOKEVIRTUAL, "cubex2/ttfr/StringRenderer", "renderString", "(Ljava/lang/String;FFIZ)I", false));
                    toInject.add(new InsnNode(I2F));
                    toInject.add(new VarInsnNode(ALOAD, 0));
                    toInject.add(new FieldInsnNode(GETFIELD, classNode.name, posX, "F"));
                    toInject.add(new InsnNode(FADD));

                    toInject.add(new FieldInsnNode(PUTFIELD, classNode.name, posX, "F"));
                    toInject.add(new JumpInsnNode(GOTO, end));

                    m.instructions.insertBefore(labelNode, toInject);
                    break;
                }
            }
        }
        {
            m = findMethod(classNode, "(Ljava/lang/String;)I", "getStringWidth", "func_78256_a");
            LabelNode labelNode = new LabelNode(new Label());

            InsnList toInject = new InsnList();
            toInject.add(new FieldInsnNode(GETSTATIC, "cubex2/ttfr/FontRendererCallback", "betterFontsEnabled", "Z"));
            toInject.add(new JumpInsnNode(IFEQ, labelNode));
            toInject.add(new VarInsnNode(ALOAD, 0));
            toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "stringCache", "Lcubex2/ttfr/StringRenderer;"));
            toInject.add(new JumpInsnNode(IFNULL, labelNode));

            toInject.add(new VarInsnNode(ALOAD, 0));
            toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "stringCache", "Lcubex2/ttfr/StringRenderer;"));
            toInject.add(new VarInsnNode(ALOAD, 1));
            toInject.add(new MethodInsnNode(INVOKEVIRTUAL, "cubex2/ttfr/StringRenderer", "getStringWidth", "(Ljava/lang/String;)I", false));
            toInject.add(new InsnNode(IRETURN));
            toInject.add(labelNode);
            m.instructions.insertBefore(m.instructions.getFirst(), toInject);
        }

        {
            m = findMethod(classNode, "(Ljava/lang/String;IZ)Ljava/lang/String;", "trimStringToWidth", "func_78262_a");
            LabelNode labelNode = new LabelNode(new Label());

            InsnList toInject = new InsnList();
            toInject.add(new FieldInsnNode(GETSTATIC, "cubex2/ttfr/FontRendererCallback", "betterFontsEnabled", "Z"));
            toInject.add(new JumpInsnNode(IFEQ, labelNode));
            toInject.add(new VarInsnNode(ALOAD, 0));
            toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "stringCache", "Lcubex2/ttfr/StringRenderer;"));
            toInject.add(new JumpInsnNode(IFNULL, labelNode));

            toInject.add(new VarInsnNode(ALOAD, 0));
            toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "stringCache", "Lcubex2/ttfr/StringRenderer;"));
            toInject.add(new VarInsnNode(ALOAD, 1));
            toInject.add(new VarInsnNode(ILOAD, 2));
            toInject.add(new VarInsnNode(ILOAD, 3));
            toInject.add(new MethodInsnNode(INVOKEVIRTUAL, "cubex2/ttfr/StringRenderer", "trimStringToWidth", "(Ljava/lang/String;IZ)Ljava/lang/String;", false));
            toInject.add(new InsnNode(ARETURN));
            toInject.add(labelNode);
            m.instructions.insertBefore(m.instructions.getFirst(), toInject);
        }

        {
            m = findMethod(classNode, "(Ljava/lang/String;I)I", "sizeStringToWidth", "func_78259_e");
            LabelNode labelNode = new LabelNode(new Label());

            InsnList toInject = new InsnList();
            toInject.add(new FieldInsnNode(GETSTATIC, "cubex2/ttfr/FontRendererCallback", "betterFontsEnabled", "Z"));
            toInject.add(new JumpInsnNode(IFEQ, labelNode));
            toInject.add(new VarInsnNode(ALOAD, 0));
            toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "stringCache", "Lcubex2/ttfr/StringRenderer;"));
            toInject.add(new JumpInsnNode(IFNULL, labelNode));

            toInject.add(new VarInsnNode(ALOAD, 0));
            toInject.add(new FieldInsnNode(GETFIELD, classNode.name, "stringCache", "Lcubex2/ttfr/StringRenderer;"));
            toInject.add(new VarInsnNode(ALOAD, 1));
            toInject.add(new VarInsnNode(ILOAD, 2));
            toInject.add(new MethodInsnNode(INVOKEVIRTUAL, "cubex2/ttfr/StringRenderer", "sizeStringToWidth", "(Ljava/lang/String;I)I", false));
            toInject.add(new InsnNode(IRETURN));
            toInject.add(labelNode);
            m.instructions.insertBefore(m.instructions.getFirst(), toInject);
        }
    }

    private boolean hasName(MethodInsnNode node, String... names)
    {
        for (String name : names)
        {
            if (node.name.equals(name))
                return true;
        }
        return false;
    }

    private MethodNode findMethod(ClassNode classNode, String desc, String... names)
    {
        for (MethodNode method : classNode.methods)
        {
            if (desc != null && !desc.equals(method.desc))
                continue;

            for (String name : names)
            {
                if (method.name.equals(name))
                    return method;
            }
        }
        return null;
    }

    private void addGetterAndSetter(ClassNode classNode, String setName, String getName, String fieldName, String fieldDesc, int returnType)
    {
        addGetter(classNode, getName, fieldName, fieldDesc, returnType);
        addSetter(classNode, setName, fieldName, fieldDesc);
    }

    private void addGetter(ClassNode classNode, String name, String fieldName, String fieldDesc, int returnType)
    {
        MethodVisitor m = classNode.visitMethod(ACC_PUBLIC, name, "()" + fieldDesc, null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 0);
        m.visitFieldInsn(GETFIELD, classNode.name, fieldName, fieldDesc);
        m.visitInsn(returnType);
        m.visitEnd();
    }

    private void addSetter(ClassNode classNode, String name, String fieldName, String fieldDesc)
    {
        MethodVisitor m = classNode.visitMethod(ACC_PUBLIC, name, "(" + fieldDesc + ")V", null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 0);
        if (fieldDesc.equals("Z"))
            m.visitVarInsn(ILOAD, 1);
        else
            m.visitVarInsn(ALOAD, 1);
        m.visitFieldInsn(PUTFIELD, classNode.name, fieldName, fieldDesc);
        m.visitInsn(RETURN);
        m.visitEnd();
    }


}
