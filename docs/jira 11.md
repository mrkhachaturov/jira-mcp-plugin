---
title: "Preparing for Jira 11.0 | Administering Jira applications Data Center 11.3"
source: "https://confluence.atlassian.com/adminjiraserver/preparing-for-jira-11-0-1557069833.html"
author:
published: 2025-07-31
created: 2026-05-21
description:
tags:
  - "clippings"
---
This documentation is intended for Jira developers who want to ensure that their existing apps are compatible with **Jira Software Data Center 11.0** and **Jira Service Management Data Center 11.0**.

**Latest version**

Here you can find information about the latest EAPs.

EAP 06 is the final EAP for Jira 11.

| **Application** | **Date** | **Number** | **Version (Maven)** | **Downloads** |
| --- | --- | --- | --- | --- |
| Jira Software | 31 Jul 2025 | 11.0.0-EAP06 | 11.0.0-m0015 | [Download EAP](https://www.atlassian.com/software/jira/download-eap) |
| Jira Service Management | 31 Jul 2025 | 11.0.0-EAP06 | 11.0.0-m0015 | [Download EAP](https://www.atlassian.com/software/jira/download-eap) |

## Summary of changes

This section provides an overview of the changes we intend to make so that you can start thinking about their impact on your apps. Once the updates are ready, we'll indicate when they’ve been implemented and in which milestone.

## Jira Software and Jira Service Management common features

Due to changes in the index structure, you’ll need to perform a *full* re-index when upgrading to Jira 11.

### Supported platform changes

Status: IMPLEMENTED (EAP 01)

In Jira 11, we’ve removed support for:

- JDK 17
- PostgreSQL 15
- PostgreSQL 14
- PostgreSQL 13
- PostgreSQL 12
- MySQL 8.0
- Oracle 18c
- MSSQL Server 2017

And we’ve added support for:

- JDK 21
- PostgreSQL 17
- MySQL 8.4 LTS

This version of Jira will only run on Java 21.

### End of support for LESS

Status: IMPLEMENTED (EAP 01)

To enhance the security, performance, and overall developer experience, we’re deprecating both the LESS web-resource transformer and the LESS Maven plugin.

We’re updating Look and Feel to use CSS variables, and all styles will either be CSS or compiled to CSS at build-time. We’re also removing LESS compilation from Java build and runtime. You can continue to use LESS or any other CSS pre-processor at build-time.

We recommend you replace the runtime transformation of LESS files with build-time compilation or move to native CSS altogether where applicable.

### End of support for the Original theme

Status: IMPLEMENTED (EAP 01)

With the new light and dark themes that brought accessibility and usability improvements, we’ve removed the original theme from the theme switcher and will fully remove it in Jira 11.

### Removal of jQuery 2 migrate

Status: IMPLEMENTED (EAP 01)

We’ve removed the jQuery 2 migrate plugin and plan to upgrade to jQuery 3 in the next EAPs to align on jQuery versions across all Data Center products. This means a significant jQuery version uplift for products containing older versions of jQuery that will make developing cross-product apps easier.

### App signing is now enabled by default

Status: IMPLEMENTED (EAP 01)

As we [communicated in February](https://community.developer.atlassian.com/t/app-signing-rollout-started-time-to-boost-app-security/89702), in 2025 we’re rolling out [app signing](https://www.atlassian.com/wac/roadmap/data-center/signed-marketplace-apps?search=signed&p=9518e78c-51). In this release, app signing is enabled by default. This feature enables better security and increases customer trust in what they install on their local instances.

If you upload your apps to Atlassian Marketplace, we’ve got you covered. Once Marketplace validates and approves your app, Atlassian will sign and trust all your apps by default; no additional action is needed. App signing affects only new app installations, previously installed apps will not undergo verification.

For details on private builds, check [this CDAC post](https://community.developer.atlassian.com/t/app-signing-rollout-started-time-to-boost-app-security/89702).

### Preparing for OpenSearch

Status: IMPLEMENTED (EAP 02)

We're laying the foundations for more agnostic option for search tooling, starting with adding an abstraction layer. This change is key to enable future OpenSearch support. [The new abstraction layer introduces a new search API](https://developer.atlassian.com/server/jira/platform/custom-field/#custom-field-sorting). Search and indexing performance will remain consistent with the existing Lucene implementation. For more details, check the following articles:

- [Search API deprecations and upgrade guide for Jira 11](https://confluence.atlassian.com/adminjiraserver/search-api-deprecations-and-upgrade-guide-for-jira-11-1573486929.html)
- [Migrating FieldIndexers in Jira 11](https://confluence.atlassian.com/adminjiraserver/migrating-fieldindexers-in-jira-11-1573487045.html)

### Spring and Jakarta upgrade

Status: IMPLEMENTED (EAP 02)

To maintain high security standards and keep dependencies supported and up to date, we’re updating Spring to the 6.x line. Spring 6 is no longer compatible with the currently used Jakarta 8, requiring us to also update the Jakarta version to [EE Platform 10](https://jakarta.ee/specifications/platform/10/ "https://jakarta.ee/specifications/platform/10/"), specifically:

- [Jakarta Servlet 6.0](https://jakarta.ee/specifications/servlet/6.0/ "https://jakarta.ee/specifications/servlet/6.0/")
- [Jakarta RESTful Web Services 3.1](https://jakarta.ee/specifications/restful-ws/3.1/ "https://jakarta.ee/specifications/restful-ws/3.1/")
- [Jakarta Dependency Injection 2.0](https://jakarta.ee/specifications/dependency-injection/2.0/ "https://jakarta.ee/specifications/dependency-injection/2.0/")
- [Jakarta Annotations 2.1](https://jakarta.ee/specifications/annotations/2.1/ "https://jakarta.ee/specifications/annotations/2.1/")
- [Jakarta Activation 2.1](https://jakarta.ee/specifications/activation/2.1/ "https://jakarta.ee/specifications/activation/2.1/")
- [Jakarta Mail 2.1](https://jakarta.ee/specifications/mail/2.1/ "https://jakarta.ee/specifications/mail/2.1/")
- [Jakarta XML Binding 4.0](https://jakarta.ee/specifications/xml-binding/4.0/ "https://jakarta.ee/specifications/xml-binding/4.0/")
- [Jakarta Bean Validation 3.0](https://jakarta.ee/specifications/bean-validation/3.0/ "https://jakarta.ee/specifications/bean-validation/3.0/")

We’re also updating all the other libraries that depend on Spring and Jakarta.

We won’t be making any changes to the Atlassian API unless they’re necessary because of the updates to the Jakarta API. There are also several API changes in Jakarta that may impact apps.

#### Known issues

Here are some known issues you might encounter after the upgrade. We’re actively working on solving them:

- The following plugins don’t start:
	- **Jira DVCS Connector Plugin** (`com.atlassian.jira.plugins.jira-bitbucket-connector-plugin`)
		- **Atlassian Troubleshooting and Support Tools** (`com.atlassian.troubleshooting.plugin-jira`)
		- **Jira Cloud Migration Assistant** (`com.atlassian.jira.migration.jira-migration-plugin`)
		- **Atlassian Jira - Plugins - ATST Health Checks** (`com.atlassian.jira.jira-atst-healthcheck-plugin`)
- After Jira startup, you must manually enable the software application in **Administration**, then **Manage apps**. The software application sometimes stops working and needs to be re-enabled in **Administration**, then **Manage apps**.
- The **Releases**, **Reports**, and **Components** pages aren’t working.
- **Issues/current search** isn’t working, but **Issues/search for issues** is working.

### Updated Tomcat protocols

Status: IMPLEMENTED (EAP 02)

We’ve updated the protocols provided by Jira extending the Tomcat protocols with support for password encryption:

<table><colgroup><col> <col> <col></colgroup><tbody><tr><th colspan="1" rowspan="1"><p><strong>Crowd protocol</strong></p></th><th colspan="1" rowspan="1"><p><strong>Based on Tomcat protocol</strong></p></th><th colspan="1" rowspan="1"><p><strong>Supported attributes for password encryption</strong></p></th></tr><tr><td colspan="1" rowspan="1"><p>com.atlassian.secrets.tomcat.protocol.Http11NioProtocolWithPasswordEncryption</p></td><td colspan="1" rowspan="1"><p>Http11NioProtocol</p></td><td colspan="1" rowspan="1"><ul><li>KeystorePass</li><li>KeyPass</li><li>SSLPassword</li><li>TruststorePass</li></ul></td></tr><tr><td colspan="1" rowspan="1"><p>com.atlassian.secrets.tomcat.protocol.Http11Nio2ProtocolWithPasswordEncryption</p></td><td colspan="1" rowspan="1"><p>Http11Nio2Protocol</p></td><td colspan="1" rowspan="1"><ul><li>KeystorePass</li><li>KeyPass</li><li>SSLPassword</li><li>TruststorePass</li></ul></td></tr><tr><td colspan="1" rowspan="1"><p>com.atlassian.secrets.tomcat.protocol.AjpNioProtocolWithPasswordEncryption</p></td><td colspan="1" rowspan="1"><p>AjpNioProtocol</p></td><td colspan="1" rowspan="1"><ul><li><p>secret</p></li></ul></td></tr><tr><td colspan="1" rowspan="1"><p>com.atlassian.secrets.tomcat.protocol.AjpNio2ProtocolWithPasswordEncryption</p></td><td colspan="1" rowspan="1"><p>AjpNio2Protocol</p></td><td colspan="1" rowspan="1"><ul><li><p>secret</p></li></ul></td></tr></tbody></table>

The APR/Native library and the APR/Native Connectors, including both AJP and HTTP, are deprecated in Tomcat 10 and will be removed starting from Tomcat 10.1.x. Specifically, the **Http11AprProtocol** (HTTP connector) and the **AjpAprProtocol** (AJP connector) are deprecated. Consequently, these are no longer supported in Jira 11:

- com.atlassian.secrets.tomcat.protocol.AjpAprProtocolWithPasswordEncryption
- com.atlassian.secrets.tomcat.protocol.Http11AprProtocolWithPasswordEncryption

### New REST endpoint for listing users

Status: IMPLEMENTED (EAP 04)

We’re introducing a new endpoint for listing users: `https://{baseurl}/rest/api/2/user/list`.

[More details](#)

This resource can’t be accessed anonymously. For performance and security reasons it doesn't indicate the total number of users available in the system.

While the first call should be done without the `cursor` parameter, for the subsequent calls you should use the value of the next `cursor` returned in the previous call. Specific values of the cursor aren’t guaranteed to be valid in the future and aren't part of the API, so you shouldn’t use them as a key for caching or storing data.

The order in which the users are returned isn't defined. It’s guaranteed that the same user won’t be returned twice in the sequence of calls. For resiliency reasons this endpoint never returns the 404 code, even if called with a `cursor` parameter that wasn’t returned in the previous call.

### Removal of a deprecated component

Status: IMPLEMENTED (EAP 04)

We’re removing the following non-API component: `com.atlassian.jira.component.pico.ComponentManager`.

### Date fields updates

Status: IMPLEMENTED (EAP 04)

`LocalDate` is now stored as milliseconds since epoch instead of days since epoch. The impacted fields are due date, work log date, and custom date picker. This change will be transparent for data access through the search API.

### Sorting on TEXT entity property

Status: IMPLEMENTED (EAP 04)

We’ve removed support for sorting on `TEXT` entity properties. This also applies to existing `TEXT` entity properties.

#### Entity properties declared with more than one type

If the same entity property is declared more than one time, only the first declaration will be considered and the subsequent declarations will be ignored.

Considering the `index-document-configuration` module below, only the `STRING` property will be created.

```
<index-document-configuration entity-key="IssueProperty" key="IssueProperty">
    <key property-key="foo.bar">
        <extract path="foo1.bar2" type="string" alias="fb">
        </extract>
    </key>
    <key property-key="foo.bar">
        <extract path="foo1.bar2" type="date">
        </extract>
    </key>
</index-document-configuration>
```

You can confirm whether a property has been ignored by configuring a logging level of `DEBUG` for the `com.atlassian.jira.search.issue.index.indexers.impl.IssuePropertyIndexExtractor` package.

### Invisible field values no longer stored

Status: IMPLEMENTED (EAP 04)

In previous versions, invisible fields weren’t indexed, they were stored only. In Jira 11, we stopped storing values for such fields, meaning the field value isn’t processed at all if invisible.

Padded string fields for number custom fields are also not stored anymore. Those fields now look like `string_customfield_xxxxx`.

### Change history index prefixes

Status: IMPLEMENTED (EAP 04)

Change history data is indexed to a Lucene index located at `{jira-home}/caches/indexesV2/changes`. Currently, the index fields are named with prefixes that correspond to the Jira fields affected by the change history.

For example, change history that contains `assignee` and `priority` fields is indexed to a document that looks like the following snippet:

<table><tbody><tr><th colspan="1" rowspan="1"><p><strong>Index field</strong></p></th><th colspan="1" rowspan="1"><p><strong>Value</strong></p></th></tr><tr><td colspan="1" rowspan="1"><p><code>issue_id</code></p></td><td colspan="1" rowspan="1"><p><code>10005</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_who</code></p></td><td colspan="1" rowspan="1"><p><code>ch-john</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_date</code></p></td><td colspan="1" rowspan="1"><p><code>1750292242712</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>assignee.ch_oldvalue</code></p></td><td colspan="1" rowspan="1"><p><code>ch-bob</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>assignee.ch_from</code></p></td><td colspan="1" rowspan="1"><p><code>ch-robert</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>assignee.ch_newvalue</code></p></td><td colspan="1" rowspan="1"><p><code>ch-charlie</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>assignee.ch_to</code></p></td><td colspan="1" rowspan="1"><p><code>ch-charles</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>assignee.ch_duration</code></p></td><td colspan="1" rowspan="1"><p><code>4102552</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>assignee.ch_nextchangedate</code></p></td><td colspan="1" rowspan="1"><p><code>1750296345264</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>priority.ch_oldvalue</code></p></td><td colspan="1" rowspan="1"><p><code>ch-3</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>priority.ch_from</code></p></td><td colspan="1" rowspan="1"><p><code>ch-medium</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>priority.ch_newvalue</code></p></td><td colspan="1" rowspan="1"><p><code>ch-4</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>priority.ch_to</code></p></td><td colspan="1" rowspan="1"><p><code>ch-low</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>priority.ch_duration</code></p></td><td colspan="1" rowspan="1"><p><code>4102552</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>priority.ch_nextchangedate</code></p></td><td colspan="1" rowspan="1"><p><code>1750296345264</code></p></td></tr></tbody></table>

In Jira 11, we no longer prefix the names of the index fields. Instead, we prefix the values. With the new structure, the document from the example above will now look like this:

<table><colgroup><col> <col></colgroup><tbody><tr><th colspan="1" rowspan="1"><p><strong>Index field</strong></p></th><th colspan="1" rowspan="1"><p><strong>Value</strong></p></th></tr><tr><td colspan="1" rowspan="1"><p><code>issue_id</code></p></td><td colspan="1" rowspan="1"><p><code>10005</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_who</code></p></td><td colspan="1" rowspan="1"><p><code>ch-john</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_date</code></p></td><td colspan="1" rowspan="1"><p><code>1750292242712</code></p></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_oldvalue</code></p></td><td colspan="1" rowspan="1"><ul><li><code>assignee:bob</code></li><li><code>status:3</code></li></ul></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_from</code></p></td><td colspan="1" rowspan="1"><ul><li><code>assignee:robert</code></li><li><code>status:medium</code></li></ul></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_newvalue</code></p></td><td colspan="1" rowspan="1"><ul><li><code>assignee:charlie</code></li><li><code>status:4</code></li></ul></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_to</code></p></td><td colspan="1" rowspan="1"><ul><li><code>ch-charles</code></li><li><code>status:low</code></li></ul></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_duration</code></p></td><td colspan="1" rowspan="1"><ul><li><code>assignee:4102552</code></li><li><code>status:4102552</code></li></ul></td></tr><tr><td colspan="1" rowspan="1"><p><code>ch_nextchangedate</code></p></td><td colspan="1" rowspan="1"><ul><li><code>assignee:0000001750296345264</code></li><li><code>status:0000001750296345264</code></li></ul><p>(Numbers are zero-padded to 19 characters)</p></td></tr></tbody></table>

If your app currently uses the change history index (for instance, to search historical changes), you’ll need to make adjustments to your queries to reflect this new data structure.

### Removal of Trusted apps

Status: IMPLEMENTED (EAP 04)

We’re removing Trusted apps to reduce the number of insecure entry points into the products. We’ve replaced this way of exchanging information between Atlassian products with more secure solutions that follow industry best practices, like the OAuth 2.0 protocol.

### Global serialization filter

Status: IMPLEMENTED (EAP 05)

We’re implementing a global serialization filter that relies on a central blocklist for Java deserialization, Velocity, and XStream. This filter is designed to block specific classes and patterns that are recognized as vulnerable to Remote Code Execution (RCE) through publicly known gadget chains.

[List of blocked classes and patterns](#)

- `br.com.anteros.dbcp.AnterosDBCPConfig`
- `com.sun.corba.se.impl.activation.ServerTableEntry`
- `com.sun.tools.javac.processing.JavacProcessingEnvironment$NameProcessIterator`
- `org.apache.commons.collections.comparators.TransformingComparator`
- `org.apache.commons.collections.comparators.ComparableComparator`
- `java.lang.ProcessBuilder`
- `javax.imageio.ImageIO$ContainsFilter`
- `jdk.nashorn.internal.objects.NativeString`
- `sun.awt.datatransfer.DataTransferer$IndexOrderComparator`
- `sun.swing.SwingLazyValue`
- `.*\$LazyIterator`
- `.*\.Lazy(?:Search)?Enumeration.*`
- `.*\$GetterSetterReflection`
- `.*\$PrivilegedGetter`
- `(?:java|sun)\.rmi\..*`
- `javax\.crypto\..*`
- `.*\$ServiceNameIterator`
- `javafx\.collections\.ObservableList\$.*`
- `.*\.bcel\..*\.util\.ClassLoader`
- `org.apache.commons.collections.comparators.ComparableComparator`
- `org.apache.commons.collections.comparators.TransformingComparator`
- `External sources`
- `org.aspectj.weaver.tools.cache.SimpleCache$StoreableCachingMap`
- `bsh.Interpreter`
- `^com\.mchange\.v2\.c3p0\..*DataSource$`
- `org.apache.click.control.Column$ColumnComparator`
- `clojure.inspector.proxy$javax.swing.table.AbstractTableModel$ff19274a`
- `clojure.lang.PersistentArrayMap`
- `org.apache.commons.beanutils.BeanComparator`
- `org.apache.commons.collections.functors.InvokerTransformer`
- `org.apache.commons.collections.functors.InstantiateTransformer`
- `org.apache.commons.collections.functors.ChainedTransformer`
- `org.apache.commons.collections.functors.ConstantTransformer`
- `org.apache.commons.collections.map.LazyMap`
- `org.apache.commons.collections4.functors.InvokerTransformer`
- `org.apache.commons.collections4.functors.InstantiateTransformer`
- `org.apache.commons.collections4.comparators.TransformingComparator`
- `org.apache.commons.collections4.functors.ChainedTransformer`
- `org.apache.commons.collections4.functors.ConstantTransformer`
- `org.apache.commons.fileupload.disk.DiskFileItem`
- `org.codehaus.groovy.runtime.ConvertedClosure`
- `org.codehaus.groovy.runtime.MethodClosure`
- `org.hibernate.engine.spi.TypedValue`
- `org.hibernate.tuple.component.AbstractComponentTuplizer`
- `org.hibernate.tuple.component.PojoComponentTuplizer`
- `com.sun.rowset.JdbcRowSetImpl`
- `org.jboss.interceptor.builder.InterceptionModelBuilder`
- `org.jboss.interceptor.builder.MethodReference`
- `org.jboss.interceptor.proxy.DefaultInvocationContextFactory`
- `org.jboss.interceptor.proxy.InterceptorMethodHandler`
- `org.jboss.interceptor.reader.ClassMetadataInterceptorReference`
- `org.jboss.interceptor.reader.DefaultMethodMetadata`
- `org.jboss.interceptor.reader.ReflectiveClassMetadata`
- `org.jboss.interceptor.reader.SimpleInterceptorMetadata`
- `org.jboss.interceptor.spi.instance.InterceptorInstantiator`
- `org.jboss.interceptor.spi.metadata.InterceptorReference`
- `org.jboss.interceptor.spi.metadata.MethodMetadata`
- `org.jboss.interceptor.spi.model.InterceptionModel`
- `org.jboss.interceptor.spi.model.InterceptionType`
- `org.jboss.weld.interceptor.builder.InterceptionModelBuilder`
- `org.jboss.weld.interceptor.builder.MethodReference`
- `org.jboss.weld.interceptor.proxy.DefaultInvocationContextFactory`
- `org.jboss.weld.interceptor.proxy.InterceptorMethodHandler`
- `org.jboss.weld.interceptor.reader.ClassMetadataInterceptorReference`
- `org.jboss.weld.interceptor.reader.DefaultMethodMetadata`
- `org.jboss.weld.interceptor.reader.ReflectiveClassMetadata`
- `org.jboss.weld.interceptor.reader.SimpleInterceptorMetadata`
- `org.jboss.weld.interceptor.spi.instance.InterceptorInstantiator`
- `org.jboss.weld.interceptor.spi.metadata.InterceptorReference`
- `org.jboss.weld.interceptor.spi.metadata.MethodMetadata`
- `org.jboss.weld.interceptor.spi.model.InterceptionModel`
- `org.jboss.weld.interceptor.spi.model.InterceptionType`
- `java.rmi.registry.Registry`
- `java.rmi.server.ObjID`
- `java.rmi.server.RemoteObjectInvocationHandler`
- `java.lang.reflect.Proxy`
- `org.python.core.PyObject`
- `org.python.core.PyBytecode`
- `org.python.core.PyFunction`
- `org.mozilla.javascript.NativeJavaObject`
- `org.mozilla.javascript.NativeJavaArray`
- `org.apache.myfaces.context.servlet.FacesContextImpl`
- `org.apache.myfaces.context.servlet.FacesContextImplBase`
- `org.apache.myfaces.el.CompositeELResolver`
- `org.apache.myfaces.el.unified.FacesELContext`
- `org.apache.myfaces.view.facelets.el.ValueExpressionMethodExpression`
- `com.sun.syndication.feed.impl.ObjectBean`
- `org.springframework.beans.factory.ObjectFactory`
- `org.springframework.aop.framework.AdvisedSupport`
- `org.springframework.aop.framework.JdkDynamicAopProxy`
- `org.springframework.aop.target.SingletonTargetSource`
- `org.springframework.core.SerializableTypeWrapper$MethodInvokeTypeProvider`
- `org.springframework.core.SerializableTypeWrapper$TypeProvider`
- `com.vaadin.data.util.NestedMethodProperty`
- `com.vaadin.data.util.PropertysetItem`
- `org.apache.wicket.util.upload.DiskFileItem`
- `org.apache.commons.configuration.ConfigurationMap`
- `flex.messaging.io.amf.ActionMessage`
- `flex.messaging.io.amf.MessageHeader`
- `java.io.InputStream`
- `java.nio.channels.Channel`
- `javax.activation.DataSource`
- `javax.sql.rowset.BaseRowSet`
- `sun.reflect.**`
- `sun.tracing.**`
- `com.sun.corba.**`
- `.*\.ws\.client\.sei\..*`
- `.*\$ProxyLazyValue`
- `com\.sun\.jndi\..*Enumerat(?:ion|or)`
- `.*\$URLData`
- `.*\.xsltc\.trax\.TemplatesImpl`
- `(javax|sun.swing)\..*LazyValue`

### Removal of deprecated components in AUI 10

Status: IMPLEMENTED (EAP 05)

We’re removing some outdated AUI 10 components with design and accessibility issues. Make sure to move to their new versions or migrate to [Atlaskit](https://atlassian.design/components):

- [Dropdown 1](https://aui.atlassian.com/aui/latest/docs/upgrades/dropdown-menu-component.html) - replaced by [Dropdown 2](https://aui.atlassian.com/aui/latest/docs/dropdown.html)
- [Toolbar 1](https://aui.atlassian.com/aui/10.0/docs/upgrades/toolbar2.html) - replaced by [Toolbar 2](https://aui.atlassian.com/aui/latest/docs/toolbar2.html)

We’re also upgrading some outdated dependencies:

- [npm: dompurify](https://www.npmjs.com/package/dompurify?activeTab=versions) upgraded from 2.5.7 to 3.1.6+. This dependency is used internally on the tooltip titles. Don’t reuse the version under the hood.
- [npm: jquery-form](https://www.npmjs.com/package/jquery-form) upgraded from 2.67 to 4.3.0+. This upgrade will ensure better jQuery 3 compatibility.

We’re also deprecating or removing the following:

- [Template - AUI Documentation](https://aui.atlassian.com/aui/9.11/docs/template.html) - we’re moving to [Soy](https://developer.atlassian.com/server/confluence/templating-in-javascript-with-soy/) or the [I18n system](https://developer.atlassian.com/server/framework/atlassian-sdk/internationalising-your-plugin/) as appropriate. This is so we can better support fewer languages with tools and prevent security issues from arising from a homebrewed templating language.
- [Dark mode (old) - AUI Documentation](https://aui.atlassian.com/aui/9.11/docs/dark-mode-old.html) - we’re removing the old unused dark theme since we’ve introduced a new one. If you’ve followed our recent guide ([Prepare your Data Center app for the dark theme](https://developer.atlassian.com/platform/marketplace/dc-apps-preparing-for-dark-theme/)) to prepare for [dark theme](https://aui.atlassian.com/aui/latest/docs/dark-mode.html) by using design tokens, you should be all set.
- Original theme - since the “light” theme is replacing it, all the fallback values will be gone.
- Redundant polyfills that are now natively supported in browsers, for example [npm: css.escape](https://www.npmjs.com/package/css.escape), custom events, and the <input> placeholder.
- [npm: @atlassian/tipsy](https://www.npmjs.com/package/@atlassian/tipsy) - this is no longer maintained and the AUI library stopped using it internally from version 9.3. You can bundle it if you wish, but we recommend moving to [Floating UI - Create tooltips, popovers, dropdowns, and more](https://floating-ui.com/) or using Atlaskit or AUI components where possible.
- [npm: trim-extra-html-whitespace](https://www.npmjs.com/package/trim-extra-html-whitespace) - this is no longer maintained and only used internally in our public documentation. This shouldn’t be needed in the browser. You can bundle it if you wish, but we recommend finding a maintained HTML minifier.
- Low-usage and renamed Soy templates, web-resources, and CSS classes.

Other changes include the Node 22 engine requirement. This will only affect using AUI via NPM, not through the running product.

### Add scopes to REST endpoints to use OAuth 2.0 2LO

Status: IMPLEMENTED (EAP 05)

We’ve introduced `@ScopesAllowed` to improve security and control over REST endpoints.

Add the `@ScopesAllowed` annotation to your endpoints to make them accessible using an OAuth 2.0 Client Credentials token (2LO). For example, this annotation requires that the access token has the WRITE scope before providing access to this endpoint.

```
@POST
@ScopesAllowed(requiredScope = "WRITE")
public void createEntity(...) {}
```

[Explore Jira OAuth 2.0 scopes for incoming links](https://confluence.atlassian.com/adminjiraserver/oauth-2-0-scopes-for-incoming-links-1115659069.html)

### Basic authentication disabled by default

Status: IMPLEMENTED (EAP 05)

We’re disabling authentication with basic authentication by default. This is a first step towards the removal of basic authentication altogether as we develop and mature alternatives to support the remaining few use cases.

### Simplification of multipart handling in WebWork

Status: IMPLEMENTED (EAP 05)

We've removed the following classes from Jira and WebWork that were no longer used by Jira and weren’t intended for use by apps. If your app relies on these classes, you'll need to transition to alternatives.

```
com.atlassian.jira.web.TempFileRemovingMultipartRequestWrapper
webwork.multipart.PellMultiPartRequest
```

---

## Jira Software features

### New look and feel for Advanced Roadmaps for Jira

Status: IMPLEMENTED (EAP 01)

We're rolling out major updates to the user interface in the Advanced Roadmaps for Jira. These enhancements provide a seamless experience across **Programs** and **Plans**, ensuring consistency in how you view and manage your projects.

Here’s what's new:

- Restructured user interface and improved administration and program management functionalities for a more intuitive experience.
- Implemented dark theme, offering a modern and visually appealing interface. To test your apps for compatibility, enable the dark theme by going to your **Profile**, then **Theme**, and select **Dark**.

With these changes, we've also addressed security and accessibility concerns and reduced technical debt by updating outdated technologies.

We’ve also implemented these changes in the upcoming Jira 10.7 that will be released before Jira 11.0.

### Removal of the deprecated Text gadget

Status: IMPLEMENTED (EAP 02)

We’ve removed the Text gadget for dashboards that we [deprecated in Jira 9.11](https://confluence.atlassian.com/jirasoftware/jira-software-9-11-x-release-notes-1272283668.html) and replaced with the Rich Text gadget to improve security in Jira.

### View your repo health with Sync history

Status: IMPLEMENTED (EAP 02)

We're introducing **Sync history** in the Distributed Version Control System (DVCS) admin section, a new feature that provides a repository sync audit log at a glance. Previously, admins couldn't access essential information about past repo syncs, making it challenging to assess the health of the sync process and debug errors.

Now, you can access a detailed list of repository syncs from the past seven days in the audit table, reducing the time you need to identify and isolate problem areas. The details include start and end time, sync status, failure reason, duration, and type of sync (soft or full).

To check the sync history of your repo:

1. Go to **Settings** , then **Applications**.
2. Go to **DVCS accounts** and open your account.
3. Next to the repo you want to check, select **Show sync history**.

![Sync repo feature in DVCS](https://confluence.atlassian.com/adminjiraserver/files/1557069833/1573487075/2/1752741048476/sync-repo.png)

Sync repo feature in DVCS

### Removal of avatar endpoints

Status: IMPLEMENTED (EAP 05)

We’ve removed the following endpoints:

- `/rest/api/2/avatar/{type}/temporary`
- `/rest/api/2/avatar/{type}/temporaryCrop`

Instead, use the following, existing alternatives:

- `/rest/api/2/universal_avatar/type/{type}/owner/{owningObjectId}/temp`
- `/rest/api/2/universal_avatar/type/{type}/owner/{owningObjectId}/avatar`

### Introducing the Jira Data Center connector for Rovo

Status: IMPLEMENTED (EAP 05)

The Jira Data Center connector facilitates seamless data synchronization from Jira Data Center to Rovo in Atlassian Cloud, allowing you to leverage Cloud-based AI capabilities without requiring a full migration to the Cloud.

With Rovo, you can centralize knowledge from various platforms, providing a unified view of search results from both Cloud and Data Center, as well as third-party tools like SharePoint and Slack. You can boost productivity with Rovo Chat’s AI-driven insights and personalized responses, all while maintaining your existing infrastructure.

**Coming soon:** You’ll be able to also include Jira Service Management Data Center projects and issues in Rovo’s unified search and AI experiences.

Rovo, including search, chat, and studio apps as well as agents, is available to customers with a Premium or Enterprise Cloud plan of Jira, Confluence, Jira Service Management, or Teamwork Collection. [Contact](https://www.atlassian.com/enterprise/contact) our team for assistance with setting up a new plan.

If you already have access to Rovo, refer to [this guide](https://support.atlassian.com/organization-administration/docs/connect-jira-data-center-to-rovo/) for step-by-step instructions on setting up the connector.

---

## Jira Service Management features

### Changed sorting behavior for custom fields

Status: IMPLEMENTED (EAP 01)

We’ve changed the sorting behavior for Assets and Organizations custom fields that support multiple values. Previously, all values were concatenated for comparison, but now only the smallest value is used which might change the order your items.

This change enhances sorting accuracy and consistency and affects the following custom fields:

- Assets object
- Assets readonly object
- Assets object (multiple) (legacy)
- Assets referenced object (multiple)
- Organizations

<table><colgroup><col> <col> <col></colgroup><tbody><tr><th colspan="1" rowspan="1"><p><strong>Custom fields</strong></p></th><th colspan="1" rowspan="1"><p><strong>Sorting order before Jira 11.0</strong></p></th><th colspan="1" rowspan="1"><p><strong>Sorting order after Jira 11.0</strong></p></th></tr><tr><td colspan="1" rowspan="1"><p>Assets-related</p></td><td colspan="1" rowspan="1"><p>Values were concatenated with commas. For example, an issue <strong>PUBLIC-27</strong> with values <code>cc</code> and <code>xx</code> would be compared as <code>cc,xx</code>.</p><p>This resulted in ranking the issue <strong>PUBLIC-27</strong> with value <code>cc,xx</code> above <strong>PUBLIC-28</strong> with value <code>cc,aa</code> when sorted in descending order.</p></td><td colspan="1" rowspan="1"><p>Only the smallest value is used for comparison. For example, an issue <strong>PUBLIC-27</strong> now uses the <code>cc</code> value for comparison, even if it contains another value.</p><p>This results in ranking the issue <strong>PUBLIC-28</strong> with the value <code>cc,aa</code> higher than the issue <strong>PUBLIC-28</strong> with the value <code>cc</code> when sorted in descending order.</p></td></tr><tr><td colspan="1" rowspan="1"><p>Organizations</p></td><td colspan="1" rowspan="1"><p>Values were concatenated with spaces. For example, an issue <strong>YP-2</strong> with values <code>cc</code> and <code>xx</code> would be compared as <code>cc xx</code>.</p><p>This resulted in ranking the issue <strong>YP-2</strong> with value <code>cc xx</code> above <strong>YP-3</strong> with value <code>cc aa</code> when sorted in descending order.</p></td><td colspan="1" rowspan="1"><p>Only the smallest value is used for comparison. For example, an issue <strong>YP-2</strong> now uses the <code>cc</code> value for comparison, even if it contains another value.</p><p>This results in ranking the issue <strong>YP-3</strong> with the value <code>cc aa</code> higher than the issue <strong>YP-2</strong> with the value <code>cc</code> when sorted in descending order.</p></td></tr></tbody></table>

Last modified on Jul 31, 2025

Was this helpful?

Yes

#### In this section

- [Search API deprecations and upgrade guide for Jira 11](https://confluence.atlassian.com/adminjiraserver/search-api-deprecations-and-upgrade-guide-for-jira-11-1573486929.html)
- [Migrating FieldIndexers in Jira 11](https://confluence.atlassian.com/adminjiraserver/migrating-fieldindexers-in-jira-11-1573487045.html)
- [Migrating Lucene collectors to Search API in Jira 11](https://confluence.atlassian.com/adminjiraserver/migrating-lucene-collectors-to-search-api-in-jira-11-1574503383.html)
- [Migrating from Lucene to Search API](https://confluence.atlassian.com/adminjiraserver/migrating-from-lucene-to-search-api-1574503524.html)
- [OpenSearch for Jira early access program](https://confluence.atlassian.com/adminjiraserver/opensearch-for-jira-early-access-program-1621492052.html)

Powered by [Confluence](http://www.atlassian.com/) and [Scroll Viewport](https://www.k15t.com/go/scroll-viewport).