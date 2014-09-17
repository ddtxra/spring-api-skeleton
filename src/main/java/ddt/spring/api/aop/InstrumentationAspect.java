package ddt.spring.api.aop;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Logging class
 * @author dteixeira
 *
 */
@Aspect
public class InstrumentationAspect {

	private static final Log LOGGER = LogFactory.getLog(InstrumentationAspect.class);

	private boolean enableInstrumentation = true;

	private AtomicLong serviceRequestIdCounter = new AtomicLong();
	private ThreadLocal<Long> serviceRequestId = new ThreadLocal<Long>();

	@Around("execution(* ddt.spring.api.*.*(..))")
	public Object logService(ProceedingJoinPoint pjp) throws Throwable {

		if (enableInstrumentation) {

			serviceRequestId.set(serviceRequestIdCounter.incrementAndGet());

			MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
			Annotation[][] annotations = methodSignature.getMethod().getParameterAnnotations();
			Object[] arguments = pjp.getArgs();

			StringBuilder sb = new StringBuilder();
			sb.append("type=Service;");
			addMethodParameters(sb, methodSignature);
			addArgumentsParameters(sb, arguments, annotations);
			sb.append("ServiceId=");
			sb.append(serviceRequestIdCounter.get());
			sb.append(";");
			Long cId = serviceRequestId.get();
			if (cId != null) {
				sb.append("controllerId=");
				sb.append(cId);
				sb.append(";");
			}

			long start = System.currentTimeMillis();
			try {

				Object result = pjp.proceed();
				addTimeElapsed(sb, System.currentTimeMillis() - start);
				addResultParameters(sb, result);
				serviceRequestId.remove();
				LOGGER.info(sb);
				return result;

			} catch (Exception e) {

				addTimeElapsed(sb, System.currentTimeMillis() - start);
				addExceptionParameters(sb, e);
				serviceRequestId.remove();
				LOGGER.info(sb);
				throw e;
			}

		} else {
			return pjp.proceed();
		}

	}

	private static StringBuilder addArgumentsParameters(StringBuilder sb, Object[] arguments, Annotation[][] annotations) {
		for (int i = 0; i < arguments.length; i++) {
			Annotation[] annots = annotations[i];
			Value v = null;
			for (Annotation a : annots) {
				if (a.annotationType().equals(Value.class)) {
					v = (Value) a;
					break;
				}
			}

			if (v == null) {
				sb.append("arg" + (i + 1));
			} else {
				sb.append(v.value());
			}

			sb.append("=");

			Object argument = arguments[i];
			if (argument == null) {
				sb.append("null");
			} else if (argument instanceof Collection) {
				sb.append(((Collection<?>) argument).size());
			} else if (argument instanceof String || argument instanceof Long || argument instanceof Short || argument instanceof Integer) {
				sb.append(argument);
			} else {
				sb.append("representation-unknown");
			}

			sb.append(";");

		}
		return sb;
	}

	private static StringBuilder addTimeElapsed(StringBuilder sb, long time) {
		sb.append("timeElapsed=");
		sb.append(time);
		sb.append(";");
		return sb;
	}

	private static StringBuilder addMethodParameters(StringBuilder sb, MethodSignature methodSignature) {
		sb.append("class=");
		sb.append(methodSignature.getDeclaringType().getSimpleName());
		sb.append(";");
		sb.append("method=");
		sb.append(methodSignature.getName());
		sb.append(";");
		sb.append("timeStart=");
		sb.append(System.currentTimeMillis());
		sb.append(";");
		return sb;
	}

	private static StringBuilder addResultParameters(StringBuilder sb, Object result) {
		sb.append("timeEnd=");
		sb.append(System.currentTimeMillis());
		sb.append(";");
		if (result instanceof Collection) {
			sb.append("resultSize=");
			sb.append(Collection.class.cast(result).size());
			sb.append(";");
		}
		return sb;
	}

	private static StringBuilder addExceptionParameters(StringBuilder sb, Exception e) {
		sb.append("timeEnd=");
		sb.append(System.currentTimeMillis());
		sb.append(";");
		sb.append("exception=");
		sb.append(";");
		if (e.getMessage() != null)
			sb.append(e.getMessage().trim().replaceAll("=", "").replaceAll(";", "="));
		return sb;
	}

	private static Map<String, String> extractSecurityInfo(Object[] arguments) {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		HashMap<String, String> map = new HashMap<String, String>();

		if (a != null) {

			if (a.getPrincipal() instanceof UserDetails) {

				UserDetails currentUserDetails = (UserDetails) a.getPrincipal();

				map.put("securityUserName", currentUserDetails.getUsername());
				map.put("securityUserRole", currentUserDetails.getAuthorities().iterator().next().getAuthority());

			} else {
				map.put("securityUserName", a.getPrincipal().toString());
			}

		} else {
			map.put("securityUserName", "unknown");
		}

		return map;

	}

	@ManagedAttribute
	public boolean getInstrumentationEnabled() {
		return enableInstrumentation;
	}

	@ManagedAttribute
	public void setInstrumentationEnabled(boolean enableInstrumentation) {
		this.enableInstrumentation = enableInstrumentation;
	}

}