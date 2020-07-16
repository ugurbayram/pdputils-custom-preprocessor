package no.uio.pdputils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmNode;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Attributes;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import org.ow2.authzforce.core.pdp.api.AttributeFqn;
import org.ow2.authzforce.core.pdp.api.DecisionRequestFactory;
import org.ow2.authzforce.core.pdp.api.DecisionRequestPreprocessor;
import org.ow2.authzforce.core.pdp.api.HashCollections;
import org.ow2.authzforce.core.pdp.api.ImmutableDecisionRequest;
import org.ow2.authzforce.core.pdp.api.IndeterminateEvaluationException;
import org.ow2.authzforce.core.pdp.api.io.BaseXacmlJaxbRequestPreprocessor;
import org.ow2.authzforce.core.pdp.api.io.IndividualXacmlJaxbRequest;
import org.ow2.authzforce.core.pdp.api.io.SingleCategoryAttributes;
import org.ow2.authzforce.core.pdp.api.io.SingleCategoryXacmlAttributesParser;
import org.ow2.authzforce.core.pdp.api.value.AttributeBag;
import org.ow2.authzforce.core.pdp.api.value.AttributeValueFactoryRegistry;
import org.ow2.authzforce.core.pdp.api.value.Bags;
import org.ow2.authzforce.core.pdp.api.value.StandardDatatypes;
import org.ow2.authzforce.core.pdp.api.value.StringValue;
import org.ow2.authzforce.xacml.identifiers.XacmlStatusCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Default XACML/XML Request preprocessor Individual Decision Requests only (no support of Multiple Decision Profile in particular)
 *
 * @author ugurb@ifi.uio.no
 */
public final class CustomRequestPreprocessor extends BaseXacmlJaxbRequestPreprocessor {
    private final static Logger logger = Logger.getLogger(CustomRequestPreprocessor.class.getName());
    private final static String prefix = "CustomAttributeFactory.";
    private final static String SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject";
    private final static String SUBJECT_ROLE = "urn:oasis:names:tc:xacml:1.0:subject:subject-id";
    private final static String ACCESS_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action";
    private final static String ACCESS_TYPE = "urn:oasis:names:tc:xacml:1.0:action:action-id";
    private static String GLOBAL_ROLE = "";
    private RestClient restClient = null;
    private static final DecisionRequestFactory<ImmutableDecisionRequest> DEFAULT_REQUEST_FACTORY = new DecisionRequestFactory<ImmutableDecisionRequest>() {

        @Override
        public ImmutableDecisionRequest getInstance(final Map<AttributeFqn, AttributeBag<?>> namedAttributes, final Map<String, XdmNode> extraContentsByCategory, final boolean returnApplicablePolicies) {
            return ImmutableDecisionRequest.getInstance(namedAttributes, extraContentsByCategory, returnApplicablePolicies);
        }
    };

    /**
     * Factory for this type of request preprocessor that allows duplicate &lt;Attribute&gt; with same meta-data in the same &lt;Attributes&gt; element of a Request (complying with XACML 3.0 core
     * spec, §7.3.3).
     */
    public static final class CustomAttributeFactory extends BaseXacmlJaxbRequestPreprocessor.Factory {
        /**
         * Request preprocessor ID, as returned by {@link #getId()}
         */
        public static final String ID = "urn:ow2:authzforce:feature:pdp:request-preproc:xacml-xml:custom-attribute";

        /**
         * Constructor
         */
        public CustomAttributeFactory() {
            super(ID);
        }


        public DecisionRequestPreprocessor<Request, IndividualXacmlJaxbRequest> getInstance(final AttributeValueFactoryRegistry datatypeFactoryRegistry, final boolean strictAttributeIssuerMatch,
                                                                                            final boolean requireContentForXPath, final Processor xmlProcessor, final Set<String> extraPdpFeatures) {
            logger.info(prefix + "CustomAttributeFactory.getInstance# Entering.");

            return new CustomRequestPreprocessor(datatypeFactoryRegistry, DEFAULT_REQUEST_FACTORY, strictAttributeIssuerMatch, true, requireContentForXPath, xmlProcessor,
                    extraPdpFeatures);
        }

        /**
         * Singleton instance of Factory used as default request preprocessor
         */
        public static final DecisionRequestPreprocessor.Factory<Request, IndividualXacmlJaxbRequest> INSTANCE = new CustomAttributeFactory();
    }


    private final DecisionRequestFactory<ImmutableDecisionRequest> reqFactory;

    /**
     * Creates instance of default request preprocessor
     *
     * @param datatypeFactoryRegistry    attribute datatype registry
     * @param requestFactory             decision request factory
     * @param strictAttributeIssuerMatch true iff strict attribute Issuer match must be enforced (in particular request attributes with empty Issuer only match corresponding AttributeDesignators with empty Issuer)
     * @param allowAttributeDuplicates   true iff duplicate Attribute (with same metadata) elements in Request (for multi-valued attributes) must be allowed
     * @param requireContentForXPath     true iff Content elements must be parsed, else ignored
     * @param xmlProcessor               XML processor for parsing Content elements iff {@code requireContentForXPath}
     * @param extraPdpFeatures           extra - not mandatory per XACML 3.0 core specification - features supported by the PDP engine. This preprocessor checks whether it is supported by the PDP before processing the
     *                                   request further.
     */
    public CustomRequestPreprocessor(final AttributeValueFactoryRegistry datatypeFactoryRegistry, final DecisionRequestFactory<ImmutableDecisionRequest> requestFactory,
                                     final boolean strictAttributeIssuerMatch, final boolean allowAttributeDuplicates, final boolean requireContentForXPath, final Processor xmlProcessor, final Set<String> extraPdpFeatures) {

        super(datatypeFactoryRegistry, strictAttributeIssuerMatch, allowAttributeDuplicates, requireContentForXPath, xmlProcessor, extraPdpFeatures);
        logger.setLevel(Level.ALL);
        logger.info(prefix + "constructor# Entering.");
        assert requestFactory != null;
        reqFactory = requestFactory;

        restClient = new RestClient();
    }

    @Override
    public List<IndividualXacmlJaxbRequest> process(final List<Attributes> attributesList, final SingleCategoryXacmlAttributesParser<Attributes> xacmlAttrsParser,
                                                    final boolean isApplicablePolicyIdListReturned, final boolean combinedDecision, final XPathCompiler xPathCompiler, final Map<String, String> namespaceURIsByPrefix)
            throws IndeterminateEvaluationException {
        logger.info(prefix + "process# Entering.");
        final Map<AttributeFqn, AttributeBag<?>> namedAttributes = HashCollections.newUpdatableMap(attributesList.size());
        final Map<String, XdmNode> extraContentsByCategory = HashCollections.newUpdatableMap(attributesList.size());
        logger.info(prefix + "process# attributesList.size=" + attributesList.size());
        logger.info(prefix + "process# attributesList=" + attributesList);
        /*
         * attributesToIncludeInResult.size() <= attributesList.size()
         */
        final List<Attributes> attributesToIncludeInResult = new ArrayList<>(attributesList.size());

        for (final Attributes jaxbAttributes : attributesList) {
            logger.info(prefix + "process# jaxbAttributes.toString=" + jaxbAttributes.toString());

            final SingleCategoryAttributes<?, Attributes> categorySpecificAttributes = xacmlAttrsParser.parseAttributes(jaxbAttributes, xPathCompiler);
            if (categorySpecificAttributes == null) {
                logger.info(prefix + "process# categorySpecificAttributes==null, continue");
                // skip this empty Attributes
                continue;
            }

            final String categoryId = categorySpecificAttributes.getCategoryId();
            final XdmNode newContentNode = categorySpecificAttributes.getExtraContent();
            logger.info(prefix + "process# categoryId=" + categoryId);

            if (newContentNode != null) {
                logger.info(prefix + "process# newContentNode!=null, newContentNode.toString=" + newContentNode.toString());
                final XdmNode duplicate = extraContentsByCategory.putIfAbsent(categoryId, newContentNode);
                /*
                 * No support for Multiple Decision Profile -> no support for repeated categories as specified in Multiple Decision Profile. So we must check duplicate attribute categories.
                 */
                if (duplicate != null) {
                    logger.info(prefix + "process# duplicate!=null");
                    throw new IndeterminateEvaluationException("Unsupported repetition of Attributes[@Category='" + categoryId
                            + "'] (feature 'urn:oasis:names:tc:xacml:3.0:profile:multiple:repeated-attribute-categories' is not supported)", XacmlStatusCode.SYNTAX_ERROR.value());
                }
            }

            /*
             * Convert growable (therefore mutable) bag of attribute values to immutable ones. Indeed, we must guarantee that attribute values remain constant during the evaluation of the request, as
             * mandated by the XACML spec, section 7.3.5: <p> <i>
             * "Regardless of any dynamic modifications of the request context during policy evaluation, the PDP SHALL behave as if each bag of attribute values is fully populated in the context before it is first tested, and is thereafter immutable during evaluation. (That is, every subsequent test of that attribute shall use the same bag of values that was initially tested.)"
             * </i></p>
             */

            final Attributes catSpecificAttrsToIncludeInResult = categorySpecificAttributes.getAttributesToIncludeInResult();
            for (final Entry<AttributeFqn, AttributeBag<?>> attrEntry : categorySpecificAttributes) {
                try {
                    if (SUBJECT_CATEGORY.equals(attrEntry.getKey().getCategory()) || ACCESS_CATEGORY.equals(attrEntry.getKey().getCategory())) {
                        if (SUBJECT_ROLE.equals(attrEntry.getKey().getId()) || ACCESS_TYPE.equals(attrEntry.getKey().getId())) {
                            logger.info(prefix + "process# Inside SUBJECT_ROLE");
                            Set<String> synonyms = new HashSet<String>();
                            Multiset elements = attrEntry.getValue().elements();
                            logger.info(prefix + "process# elements=" + elements);
                            Iterator it = elements.iterator();
                            while (it.hasNext()) {
                                StringValue element = (StringValue) it.next();
                                synonyms.add(element.toString().toLowerCase());
                                Document doc = null;
                                if (SUBJECT_ROLE.equals(attrEntry.getKey().getId())) {
                                    if (GLOBAL_ROLE == "") {
                                        GLOBAL_ROLE = element.toString();
                                    }
                                    doc = restClient.getSemanticReasonerSubjectRoleResult(element.toString());
                                } else if (ACCESS_TYPE.equals(attrEntry.getKey().getId())) {
                                    doc = restClient.getSemanticReasonerAccessTypeResult(element.toString(), GLOBAL_ROLE);
                                }
                                logger.info(prefix + "process# got doc=" + doc + " for element=" + element);
                                if (doc != null && doc.isContext()) {
                                    synonyms.addAll(doc.getAttributes().stream().map(String::toLowerCase).collect(Collectors.toList()));
                                }
                            }
                            if (synonyms.size() > 0) {
                                logger.info(prefix + "process# BEFORE UPDATE getKey=" + attrEntry.getKey());
                                logger.info(prefix + "process# BEFORE UPDATE getValue=" + attrEntry.getValue());
                                Collection<StringValue> collection = getCollection(synonyms);

                                //TODO currently accesstype is not working. Check if GLOBAL_ROLE reaches correctly
                                final AttributeBag<?> newAttributeValues = Bags.newAttributeBag(StandardDatatypes.STRING, collection);
                                attrEntry.setValue(newAttributeValues);
                                logger.info(prefix + "process# UPDATED ELEMENTS=" + attrEntry.getKey());
                                logger.info(prefix + "process# UPDATED ELEMENTS=" + attrEntry.getValue());
                            }
                        }
                    }
                    namedAttributes.put(attrEntry.getKey(), attrEntry.getValue());
                } catch (Exception ex) {
                    logger.info(prefix + "process# error=" + ex.toString());
                    logger.info(prefix + "process# error=" + ex);
                }
                logger.info(prefix + "process# attrEntry.getKey=" + attrEntry.getKey() + ", attrEntry.getValue=" + attrEntry.getValue());
            }

            if (catSpecificAttrsToIncludeInResult != null) {
                logger.info(prefix + "process# catSpecificAttrsToIncludeInResult!=null, catSpecificAttrsToIncludeInResult=" + catSpecificAttrsToIncludeInResult);
                attributesToIncludeInResult.add(catSpecificAttrsToIncludeInResult);
            }
        }

        IndividualXacmlJaxbRequest req = new IndividualXacmlJaxbRequest(reqFactory.getInstance(namedAttributes, extraContentsByCategory, isApplicablePolicyIdListReturned), ImmutableList
                .copyOf(attributesToIncludeInResult));
        return Collections.singletonList(req);
    }

    private Collection<StringValue> getCollection(Set<String> synonyms) {
        List<StringValue> list = new ArrayList();

        for (String synonym : synonyms) {
            list.add(new StringValue(synonym));
        }

        return list;
    }


}