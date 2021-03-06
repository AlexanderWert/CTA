package rocks.cta.dflt.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import rocks.cta.api.core.AdditionalInformation;
import rocks.cta.api.core.Callable;
import rocks.cta.api.core.SubTrace;
import rocks.cta.api.utils.CallableIterator;
import rocks.cta.api.utils.StringUtils;

/**
 * Default Callable implementation. Contains all information like timings, method characteristcs,
 * etc. locally in the object.
 * 
 * @author Alexander Wert
 *
 */
public class CallableImpl implements Callable, Serializable {

	/**
	 * Nano to milli seconds transformer factor.
	 */
	private static final double NANOS_TO_MILLIS_FACTOR = 0.000001;

	/**
	 * 
	 */
	private static final long serialVersionUID = -4349788604807234361L;

	/**
	 * Callables called by this Callable.
	 */
	private List<Callable> children;

	/**
	 * Callable that called this Callable.
	 */
	private CallableImpl parent;

	/**
	 * Entry timestamp.
	 */
	private long entryTime = -1;

	/**
	 * Execution time (exclusive duration).
	 */
	private long executionTime = -1;

	/**
	 * Response time.
	 */
	private long responseTime = -1;

	/**
	 * CPU time consumed by this Callable.
	 */
	private long cpuTime = -1;

	/**
	 * Signature identifier.
	 */
	private int signatureId;

	/**
	 * List of label identifiers. Identifiers point to a repository in the containing trace.
	 */
	private List<Integer> labelIds;

	/**
	 * List of additional information objects.
	 */
	private List<AdditionalInformation> additionInfos;

	/**
	 * Indicates whether this Callable conducts a SubTrace invocation.
	 */
	private boolean isSubTraceInvocation = false;

	/**
	 * Indicates whether this Callable conducts an asynchronous SubTrace invocation.
	 */
	private boolean isAsyncInvocation = false;

	/**
	 * Containing SubTrace.
	 */
	private SubTraceImpl parentSubTrace;

	/**
	 * If this Callable does a SubTrace invocation, this member variable points to the invoked
	 * SubTrace.
	 */
	private SubTraceImpl targetSubTrace;

	/**
	 * Number of elements below this callable.
	 */
	private int childCount = 0;

	/**
	 * Default constructor.
	 */
	public CallableImpl() {
	}

	/**
	 * Constructor.
	 * 
	 * @param parent
	 *            Callable that called this Callable
	 * @param containingSubTrace
	 *            the SubTrace containing this Callable
	 */
	public CallableImpl(CallableImpl parent, SubTraceImpl containingSubTrace) {
		super();
		this.parent = parent;
		if (parent != null) {
			parent.addCallee(this);
		}
		this.parentSubTrace = containingSubTrace;
	}

	@Override
	public List<Callable> getCallees() {
		if (children == null) {
			return Collections.emptyList();
		} else {
			return Collections.unmodifiableList(children);
		}

	}

	/**
	 * Adds a new child Callable.
	 * 
	 * @param callee
	 *            a Callables called by this Callable
	 */
	private void addCallee(Callable callee) {
		if (children == null) {
			children = new ArrayList<Callable>();
		}
		children.add(callee);
		updateChildCount(callee.getChildCount() + 1);
	}

	@Override
	public Callable getParent() {
		return parent;
	}

	@Override
	public SubTrace getContainingSubTrace() {
		return parentSubTrace;
	}

	@Override
	public long getExecutionTime() {
		return executionTime;
	}

	public void setExecutionTime(long execTime) {
		executionTime = execTime;
	}

	@Override
	public long getResponseTime() {
		return responseTime;
	}

	public void setResponseTime(long responseTime) {
		this.responseTime = responseTime;
	}

	@Override
	public long getCPUTime() {
		return cpuTime;
	}

	public void setCPUTime(long cpuTime) {
		this.cpuTime = cpuTime;
	}

	@Override
	public long getEntryTime() {
		return entryTime;
	}

	public void setEntryTime(long entryTimestamp) {
		entryTime = entryTimestamp;
	}

	@Override
	public long getExitTime() {
		return entryTime + Math.round(((double) responseTime) * NANOS_TO_MILLIS_FACTOR);
	}

	@Override
	public String getSignature() {
		return resolveSignature().toString();
	}

	/**
	 * Sets the signature of this Callable.
	 * 
	 * @param returnType
	 *            full qualified name of the return type
	 * @param packageName
	 *            full package name
	 * @param className
	 *            simple class name
	 * @param methodName
	 *            simple method name
	 * @param parameterTypes
	 *            list of full qualified parameter types
	 */
	public void setSignature(String returnType, String packageName, String className, String methodName, List<String> parameterTypes) {
		signatureId = ((TraceImpl) getContainingSubTrace().getContainingTrace()).registerSignature(returnType, packageName, className, methodName, parameterTypes);
	}

	@Override
	public String getMethodName() {
		return resolveSignature().getMethodName();
	}

	@Override
	public String getClassName() {
		return resolveSignature().getClassName();
	}

	@Override
	public String getPackageName() {
		return resolveSignature().getPackageName();
	}

	@Override
	public List<String> getParameterTypes() {
		return resolveSignature().getParameterTypes();
	}

	@Override
	public String getReturnType() {
		return resolveSignature().getReturnType();
	}

	@Override
	public boolean isConstructor() {
		return resolveSignature().isConstructor();
	}

	@Override
	public List<String> getLabels() {
		if (labelIds == null) {
			return Collections.emptyList();
		}
		List<String> labels = new ArrayList<String>();
		TraceImpl trace = ((TraceImpl) getContainingSubTrace().getContainingTrace());
		for (int id : labelIds) {
			labels.add(trace.getStringConstant(id));
		}
		return Collections.unmodifiableList(labels);
	}

	/**
	 * Attaches a label to this Callable.
	 * 
	 * @param label
	 *            lable to add
	 */
	public void attachLabel(String label) {

		if (labelIds == null) {
			labelIds = new ArrayList<Integer>();
		}

		int hash = ((TraceImpl) getContainingSubTrace().getContainingTrace()).registerStringConstant(label);
		labelIds.add(hash);
	}

	@Override
	public boolean hasLabel(String label) {
		if (labelIds == null) {
			return false;
		}
		return labelIds.contains(label.hashCode());
	}

	@Override
	public Collection<AdditionalInformation> getAdditionalInformation() {
		if (additionInfos == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(additionInfos);
	}

	/**
	 * Attaches an additional information object to this Callable.
	 * 
	 * @param additionalInfo
	 *            additional information to attach
	 */
	public void attachAdditionalInformation(AdditionalInformation additionalInfo) {
		if (additionInfos == null) {
			additionInfos = new ArrayList<AdditionalInformation>();
		}
		additionInfos.add(additionalInfo);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends AdditionalInformation> Collection<T> getAdditionalInformation(Class<T> type) {
		List<T> result = new ArrayList<T>();
		for (AdditionalInformation aInfo : additionInfos) {
			if (type.isAssignableFrom(aInfo.getClass())) {
				result.add((T) aInfo);
			}
		}
		return Collections.unmodifiableList(result);
	}

	@Override
	public boolean isSubTraceInvocation() {
		return isSubTraceInvocation;
	}

	/**
	 * Specifies whether this Callable represents a SubTrace invocation.
	 * 
	 * @param isSTInvocation
	 *            true, if this Callable represents a SubTrace invocation
	 */
	public void setIsSubTraceInvocation(boolean isSTInvocation) {
		isSubTraceInvocation = isSTInvocation;
	}

	@Override
	public SubTrace getInvokedSubTrace() {
		return targetSubTrace;
	}

	/**
	 * If this Callable represents a SubTrace invocation, this method allows to set the invoked
	 * SubTrace.
	 * 
	 * @param subTrace
	 *            the invoked SubTrace
	 */
	public void setInvokedSubTrace(SubTraceImpl subTrace) {
		if (!isSubTraceInvocation) {
			throw new IllegalStateException("Cannot specify traget SubTrace property to a Callable that is not a SubTrace invocation. " + "Make sure that subTraceInvocation is set to true.");
		}
		targetSubTrace = subTrace;
	}

	@Override
	public boolean isAsyncInvocation() {
		return isAsyncInvocation;
	}

	/**
	 * If this Callable represents a SubTrace invocation, this method allows to set whether the
	 * invocation is asynchronous or not.
	 * 
	 * @param isAsyncInvocation
	 *            boolean value
	 */
	public void setIsAyncInvocation(boolean isAsyncInvocation) {
		if (!isSubTraceInvocation) {
			throw new IllegalStateException("Cannot specify asynchronous invocation property to a Callable that is not a SubTrace invocation. " + "Make sure that subTraceInvocation is set to true.");
		}
		this.isAsyncInvocation = isAsyncInvocation;
	}

	/**
	 * Resolves the Signature object from the repository in the corresponding Trace object.
	 * 
	 * @return returns the Signature object for this Callable
	 */
	private Signature resolveSignature() {
		Signature signature = ((TraceImpl) getContainingSubTrace().getContainingTrace()).getSignature(signatureId);
		if (signature == null) {
			throw new IllegalStateException("Signature has not been specified, yet!");
		}
		return signature;
	}

	@Override
	public String toString() {
		return StringUtils.getStringRepresentation(this);
	}

	@Override
	public int getChildCount() {
		return childCount;
	}

	/**
	 * Updates the child count of this node by increasing the current child count by the passed
	 * childCountIncrease.
	 * 
	 * @param childCountIncrease
	 *            the childCount to increment the current child count by
	 */
	private void updateChildCount(int childCountIncrease) {
		this.childCount += childCountIncrease;
		if (parent != null) {
			parent.updateChildCount(childCountIncrease);
		}
	}

	@Override
	public Iterator<Callable> iterator() {
		return new CallableIterator(this);
	}
}
