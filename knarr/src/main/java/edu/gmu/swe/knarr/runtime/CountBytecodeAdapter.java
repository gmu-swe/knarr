package edu.gmu.swe.knarr.runtime;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.ClassVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.MethodVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Opcodes;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.tree.*;

public class CountBytecodeAdapter extends ClassVisitor implements Opcodes {

  private static final int LIKELY_NUMBER_OF_LINES_PER_BLOCK = 7;

  public CountBytecodeAdapter(ClassVisitor classVisitor, boolean skipFrames) {
    super(ASM6, classVisitor);
  }

  private String className;

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    this.className = name;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
    MethodBlockNode mbn = new MethodBlockNode(access, name, descriptor, signature, exceptions,mv);
    return (Coverage.isCovEnabled(className) ? mbn : mv);
  }

  private static class MethodBlockNode extends MethodNode {
    private final MethodVisitor underlyingMV;

    public MethodBlockNode(int access, String name, String descriptor, String signature, String[] exceptions,MethodVisitor mv) {
      super(ASM6, access, name, descriptor, signature, exceptions);
      this.underlyingMV = mv;
    }

    @Override
    public void visitEnd() {
      super.visitEnd();

      ArrayList<Block> blocks = analyze(this);

      // Add bytecode count in reverse to not change the indexes of the blocks
      for (int i = blocks.size() - 1 ; i >= 0 ; i--) {
        Block b = blocks.get(i);
        int size = 0;

        for (int j = b.firstInstruction ; j <= b.lastInstruction ; j++) {
          if (this.instructions.get(j) instanceof LabelNode || this.instructions.get(j) instanceof FrameNode)
            continue;
          else
            size += 1;
        }

        if (size > 0) {
          InsnList countBytecode = new InsnList();
          countBytecode.add(new LdcInsnNode(size));
          countBytecode.add(new FieldInsnNode(GETSTATIC, Coverage.INTERNAL_NAME, "count", "I"));
          countBytecode.add(new InsnNode(IADD));
          countBytecode.add(new FieldInsnNode(PUTSTATIC, Coverage.INTERNAL_NAME, "count", "I"));

          this.instructions.insertBefore(this.instructions.get(b.lastInstruction), countBytecode);
        }
      }

      accept(underlyingMV);
    }
  }

  public static ArrayList<Block> analyze(final MethodNode mn) {
    final ArrayList<Block> blocks = new ArrayList<>(mn.instructions.size());

    final Set<LabelNode> jumpTargets = findJumpTargets(mn.instructions);

    // not managed to construct bytecode to show need for this
    // as try catch blocks usually have jumps at their boundaries anyway.
    // so possibly useless, but here for now. Because fear.
    addtryCatchBoundaries(mn, jumpTargets);

    Set<Integer> blockLines = smallSet();
    int lastLine = Integer.MIN_VALUE;

    final int lastInstruction = mn.instructions.size() - 1;

    int blockStart = 0;
    for (int i = 0; i != mn.instructions.size(); i++) {

      final AbstractInsnNode ins = mn.instructions.get(i);

      if (ins instanceof LineNumberNode) {
        final LineNumberNode lnn = (LineNumberNode) ins;
        blockLines.add(lnn.line);
        lastLine = lnn.line;
      } else if (jumpTargets.contains(ins) && (blockStart != i)) {
        if (blockLines.isEmpty() && blocks.size() > 0 && !blocks
            .get(blocks.size() - 1).getLines().isEmpty()) {
          blockLines.addAll(blocks.get(blocks.size() - 1).getLines());
        }
        blocks.add(new Block(blockStart, i - 1, blockLines));
        blockStart = i;
        blockLines = smallSet();
      } else if (endsBlock(ins)) {
        if (blockLines.isEmpty() && blocks.size() > 0 && !blocks
            .get(blocks.size() - 1).getLines().isEmpty()) {
          blockLines.addAll(blocks.get(blocks.size() - 1).getLines());
        }
        blocks.add(new Block(blockStart, i, blockLines));
        blockStart = i + 1;
        blockLines = smallSet();
      } else if ((lastLine != Integer.MIN_VALUE) && isInstruction(ins)) {
        blockLines.add(lastLine);
      }
    }

    // this will not create a block if the last block contains only a single
    // instruction.
    // In the case of the hanging labels that eclipse compiler seems to generate
    // this is desirable.
    // Not clear if this will create problems in other scenarios
    if (blockStart != lastInstruction) {
      blocks.add(new Block(blockStart, lastInstruction, blockLines));
    }

    return blocks;

  }

  private static HashSet<Integer> smallSet() {
    return new HashSet<>(LIKELY_NUMBER_OF_LINES_PER_BLOCK);
  }

  private static boolean isInstruction(final AbstractInsnNode ins) {
    return !((ins instanceof LabelNode) || (ins instanceof FrameNode));
  }

  private static void addtryCatchBoundaries(final MethodNode mn,
      final Set<LabelNode> jumpTargets) {
    for (final Object each : mn.tryCatchBlocks) {
      final TryCatchBlockNode tcb = (TryCatchBlockNode) each;
      jumpTargets.add(tcb.handler);
    }
  }

  private static boolean endsBlock(final AbstractInsnNode ins) {
    return (ins instanceof JumpInsnNode) || isReturn(ins)
        || isMightThrowException(ins);
  }

  private static boolean isMightThrowException(int opcode) {
    switch (opcode) {
    //division by 0
    case IDIV:
    case FDIV:
    case LDIV:
    case DDIV:
      //NPE
    case MONITORENTER:
    case MONITOREXIT: //or illegalmonitor
      //ArrayIndexOutOfBounds or null pointer
    case IALOAD:
    case LALOAD:
    case SALOAD:
    case DALOAD:
    case BALOAD:
    case FALOAD:
    case CALOAD:
    case AALOAD:
    case IASTORE:
    case LASTORE:
    case SASTORE:
    case DASTORE:
    case BASTORE:
    case FASTORE:
    case CASTORE:
    case AASTORE:
    case CHECKCAST: //incompatible cast
      //trigger class initialization
//    case NEW: //will break powermock :(
    case NEWARRAY:
    case GETSTATIC:
    case PUTSTATIC:
    case GETFIELD:
    case PUTFIELD:
      return true;
    default:
      return false;
    }
  }

  private static boolean isMightThrowException(AbstractInsnNode ins) {
    switch (ins.getType()) {
    case AbstractInsnNode.MULTIANEWARRAY_INSN:
      return true;
    case AbstractInsnNode.INSN:
    case AbstractInsnNode.TYPE_INSN:
    case AbstractInsnNode.FIELD_INSN:
      return isMightThrowException(ins.getOpcode());
    case AbstractInsnNode.METHOD_INSN:
      return true;
    default:
      return false;
    }

  }

  private static boolean isReturn(final AbstractInsnNode ins) {
    final int opcode = ins.getOpcode();
    switch (opcode) {
    case RETURN:
    case ARETURN:
    case DRETURN:
    case FRETURN:
    case IRETURN:
    case LRETURN:
    case ATHROW:
      return true;
    }

    return false;

  }

  private static Set<LabelNode> findJumpTargets(final InsnList instructions) {
    final Set<LabelNode> jumpTargets = new HashSet<>();
    final ListIterator<AbstractInsnNode> it = instructions.iterator();
    while (it.hasNext()) {
      final AbstractInsnNode o = it.next();
      if (o instanceof JumpInsnNode) {
        jumpTargets.add(((JumpInsnNode) o).label);
      } else if (o instanceof TableSwitchInsnNode) {
        final TableSwitchInsnNode twn = (TableSwitchInsnNode) o;
        jumpTargets.add(twn.dflt);
        jumpTargets.addAll(twn.labels);
      } else if (o instanceof LookupSwitchInsnNode) {
        final LookupSwitchInsnNode lsn = (LookupSwitchInsnNode) o;
        jumpTargets.add(lsn.dflt);
        jumpTargets.addAll(lsn.labels);
      }
    }
    return jumpTargets;
  }


  private static final class Block {
    private final int          firstInstruction;
    private final int          lastInstruction;
    private int                size;
    private final Set<Integer> lines;

    public Block(final int firstInstruction, final int lastInstruction,
                 final Set<Integer> lines) {
      this.firstInstruction = firstInstruction;
      this.lastInstruction = lastInstruction;
      this.lines = lines;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = (prime * result) + this.firstInstruction;
      result = (prime * result) + this.lastInstruction;
      return result;
    }

    @Override
    public boolean equals(final Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Block other = (Block) obj;
      if (this.firstInstruction != other.firstInstruction) {
        return false;
      }
      if (this.lastInstruction != other.lastInstruction) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "Block [firstInstruction=" + this.firstInstruction
              + ", lastInstruction=" + this.lastInstruction + "]";
    }

    public boolean firstInstructionIs(final int ins) {
      return this.firstInstruction == ins;
    }

    public Set<Integer> getLines() {
      return this.lines;
    }

    public int getFirstInstruction() {
      return this.firstInstruction;
    }

    public int getLastInstruction() {
      return this.lastInstruction;
    }
  }

}
