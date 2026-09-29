package com.ApnaAspatal.portal.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import com.ApnaAspatal.portal.triage.dto.SymptomRequest;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerRequest;

/**
 * The operations that perform several writes must run inside one transaction,
 * so a failure part-way cannot leave a session half-updated - for example a
 * changed parent answer beside the follow-up answers it invalidated.
 *
 * <p>An annotation alone proves nothing: {@code @Transactional} only takes effect
 * through a Spring proxy. This checks the beans really are proxied and that an
 * unchecked exception - every exception these services throw - rolls back.
 */
@SpringBootTest
class TransactionBoundaryTest {

    @Autowired
    private TriageAnswerService triageAnswerService;

    @Autowired
    private TriageEvaluationService triageEvaluationService;

    @Autowired
    private TriageResultService triageResultService;

    @Autowired
    private SymptomService symptomService;

    @Test
    void submittingAnAnswerIsAtomic() throws NoSuchMethodException {
        assertRollsBackAsOneUnit(triageAnswerService, "submitAnswer", Long.class, TriageAnswerRequest.class);
    }

    @Test
    void evaluatingASessionIsAtomic() throws NoSuchMethodException {
        assertRollsBackAsOneUnit(triageEvaluationService, "evaluate", Long.class);
    }

    @Test
    void evaluatingAndStampingTheResultIsAtomic() throws NoSuchMethodException {
        // The lock, the evaluation, the version stamp, and the audit record commit together.
        assertRollsBackAsOneUnit(triageResultService, "evaluate", Long.class);
    }

    @Test
    void addingASymptomIsAtomic() throws NoSuchMethodException {
        // The symptom, the inputs-version change, and the audit record commit together.
        assertRollsBackAsOneUnit(symptomService, "addSymptom", Long.class, SymptomRequest.class);
    }

    private static void assertRollsBackAsOneUnit(Object bean, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        assertThat(AopUtils.isAopProxy(bean)).as("%s is a transactional proxy", methodName).isTrue();

        Class<?> target = AopUtils.getTargetClass(bean);
        Method method = target.getMethod(methodName, parameterTypes);
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(method, target);

        assertThat(attribute).as("%s is transactional", methodName).isNotNull();
        assertThat(attribute.isReadOnly()).as("%s may write", methodName).isFalse();
        assertThat(attribute.rollbackOn(new RuntimeException())).as("%s rolls back", methodName).isTrue();
    }
}
