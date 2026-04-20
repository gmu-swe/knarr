package edu.gmu.swe.knarr.runtime;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.UnaryOperation;

public class PathConditionWrapper implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = -8116156330739369246L;
	public Expression constraints = null;
	public int size = 0;
	private int max = Integer.parseInt(System.getProperty("MAX_CONSTRAINTS","-1"));

	/**
	 * Ordered list of branch constraints (those contributed by the
	 * {@link PathConstraintListener#onIntBranch} / {@code onIntCmpBranch} /
	 * {@code onTableSwitch} / {@code onLookupSwitch} / {@code recordIntCmp}
	 * / {@code recordRealCmp} / string-predicate paths). Anchors (bounds,
	 * concrete-cell equalities, symbolic-tag init constraints, array store
	 * history) are NOT added here even though they still live in the AND
	 * tree via {@link #constraints}.
	 *
	 * <p>Enables concolic branch negation: given branch index {@code i}, a
	 * driver can request a new constraint expression of the form
	 * {@code anchors ∧ b0 ∧ b1 ∧ ... ∧ b(i-1) ∧ NOT(bi)} and feed the
	 * solver's model back to the parser as the next input, flipping the
	 * parser's control flow at branch {@code i}.
	 */
	public LinkedList<Expression> branchConstraints = new LinkedList<>();

	public PathConditionWrapper() {
	}

	public synchronized void _addDet(Operation op) {
		if (constraints == null)
			constraints = op;
		else {
			if (max < 0 || size < max) {
				size += 1;
				constraints = new BinaryOperation(Operator.AND, constraints, op);
			}
		}
	}

	public synchronized Expression _addDet(Operator op, Expression l, Expression r) {
		Operation ret = new BinaryOperation(op, l, r);
		if (constraints == null)
			constraints = ret;
		else {
			if (max < 0 || size < max) {
				size += 1;
				constraints = new BinaryOperation(Operator.AND, constraints, ret);
			}
		}

		return ret;
	}

	public synchronized Expression _addDet(Operator op, Expression t1) {
		Operation ret = new UnaryOperation(op, t1);
		if (constraints == null)
			constraints = ret;
		else {
			if (max < 0 || size < max) {
			    size += 1;
				constraints = new BinaryOperation(Operator.AND, constraints, ret);
            }
		}

		return ret;
	}

	/**
	 * Append {@code op} to the AND tree AND register it in the branch list.
	 * The branch list stores the exact same {@link Expression} reference
	 * that's embedded in the AND tree, so identity-based anchor/branch
	 * separation (see {@link #anchorConstraints()}) works reliably even
	 * when the expression would {@code .equals} an anchor.
	 *
	 * <p>If the MAX_CONSTRAINTS size cap drops the constraint, the branch
	 * is NOT registered, so branch indices stay aligned with what the
	 * solver will see. Caller must check the size cap state itself if it
	 * cares about per-call registration.
	 */
	public synchronized void _addBranchDet(Expression branch) {
		if (!(branch instanceof Operation)) return;
		// Mirror the _addDet(Operation) accept/reject logic exactly so
		// the branch list mirrors the AND-tree leaves 1:1.
		if (constraints == null) {
			constraints = branch;
			branchConstraints.add(branch);
			return;
		}
		if (max < 0 || size < max) {
			size += 1;
			constraints = new BinaryOperation(Operator.AND, constraints, branch);
			branchConstraints.add(branch);
		}
	}

	/**
	 * Fast-path used by callers that want to build the branch Operation
	 * in-line. Produces the same Operation as {@link #_addDet(Operator,
	 * Expression, Expression)} and tags it as a branch. Returns the
	 * built expression so callers can keep a handle on it.
	 */
	public synchronized Expression _addBranchDet(Operator op, Expression l, Expression r) {
		Operation built = new BinaryOperation(op, l, r);
		_addBranchDet(built);
		return built;
	}

	/**
	 * Legacy three-arg form retained for source compatibility. The
	 * {@code site} and {@code taken} arguments are ignored — the
	 * heuristic mutators derive fingerprints from the branch expression
	 * itself, not from per-call metadata (see
	 * {@code PilotRunner.guidedSiteKey}).
	 */
	public synchronized void _addBranchDet(Expression branch, String site, boolean taken) {
		_addBranchDet(branch);
	}

	public synchronized Expression _addBranchDet(Operator op, Expression l, Expression r,
	                                              String site, boolean taken) {
		return _addBranchDet(op, l, r);
	}

	/**
	 * Collects all AND-spine leaves that are NOT in {@link #branchConstraints}
	 * — i.e. the anchor constraints (bounds, concrete-cell equalities,
	 * sign-extension OR masks, array-store history, string-length anchors,
	 * etc.).
	 */
	public synchronized List<Expression> anchorConstraints() {
		ArrayList<Expression> anchors = new ArrayList<>();
		if (constraints == null) return anchors;
		java.util.IdentityHashMap<Expression, Boolean> branchIds =
				new java.util.IdentityHashMap<>();
		for (Expression b : branchConstraints) branchIds.put(b, Boolean.TRUE);
		java.util.ArrayDeque<Expression> stack = new java.util.ArrayDeque<>();
		stack.push(constraints);
		while (!stack.isEmpty()) {
			Expression e = stack.pop();
			if (e instanceof BinaryOperation) {
				BinaryOperation b = (BinaryOperation) e;
				if (b.getOperator() == Operator.AND && !branchIds.containsKey(e)) {
					// Descend — except that an AND could itself be a
					// registered branch (string predicate path), in which
					// case we treat it as a leaf.
					for (Expression child : b.getOperands()) stack.push(child);
					continue;
				}
			}
			if (!branchIds.containsKey(e)) {
				anchors.add(e);
			}
		}
		return anchors;
	}

	/**
	 * Build a fresh Expression tree shaped
	 * {@code (anchors) ∧ b0 ∧ b1 ∧ ... ∧ b(i-1) ∧ NOT(bi)} where the
	 * {@code bN} are drawn from {@link #branchConstraints} in record order.
	 * Returns {@code null} if {@code branchIndex} is out of range or the
	 * branch list is empty. Does NOT mutate this wrapper's {@link #constraints}
	 * so the caller's concrete execution state is preserved across calls.
	 */
	public synchronized Expression buildFlippedConstraints(int branchIndex) {
		if (branchIndex < 0 || branchIndex >= branchConstraints.size()) {
			return null;
		}
		Expression acc = null;
		for (Expression a : anchorConstraints()) {
			acc = andJoin(acc, a);
		}
		int i = 0;
		for (Expression b : branchConstraints) {
			if (i == branchIndex) {
				acc = andJoin(acc, new UnaryOperation(Operator.NOT, b));
				break;
			}
			acc = andJoin(acc, b);
			i++;
		}
		return acc;
	}

	private static Expression andJoin(Expression acc, Expression e) {
		if (e == null) return acc;
		if (acc == null) return e;
		return new BinaryOperation(Operator.AND, acc, e);
	}

	public synchronized int branchCount() {
		return branchConstraints.size();
	}

	public synchronized void clearBranches() {
		branchConstraints.clear();
	}

}
