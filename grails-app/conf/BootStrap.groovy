/*
 * Copyright 2011-2017 CaboLabs Health Informatics
 *
 * The EHRServer was designed and developed by Pablo Pazos Gutierrez <pablo.pazos@cabolabs.com> at CaboLabs Health Informatics (www.cabolabs.com).
 *
 * You can't remove this notice from the source code, you can't remove the "Powered by CaboLabs" from the UI, you can't remove this notice from the window that appears then the "Powered by CaboLabs" link is clicked.
 *
 * Any modifications to the provided source code can be stated below this notice.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.cabolabs.ehrserver.openehr.common.generic.PatientProxy
import grails.util.Holders

import com.cabolabs.security.RequestMap
import com.cabolabs.security.User
import com.cabolabs.security.Role
import com.cabolabs.security.UserRole
import com.cabolabs.security.Organization
import com.cabolabs.ehrserver.query.*
import com.cabolabs.ehrserver.ehr.clinical_documents.*
import com.cabolabs.ehrserver.openehr.common.change_control.*
import com.cabolabs.ehrserver.openehr.common.generic.*
import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityUtils

import com.cabolabs.ehrserver.openehr.ehr.Ehr
import com.cabolabs.openehr.opt.manager.OptManager // load opts
import com.cabolabs.ehrserver.api.structures.PaginatedResults

import com.cabolabs.ehrserver.account.*

import grails.converters.*
import groovy.xml.MarkupBuilder
import org.codehaus.groovy.grails.web.converters.marshaller.NameAwareMarshaller
import com.cabolabs.ehrserver.ResourceService

import com.cabolabs.ehrserver.notification.*

import grails.util.Environment

class BootStrap {

   private static String PS = System.getProperty("file.separator")
   
   def mailService
   def grailsApplication
   def resourceService
   
   def init = { servletContext ->
      
      def working_folder = new File('.')
      println "working folder: "+ working_folder.absolutePath
      
      
      // file system checks
      def commits_repo = new File(Holders.config.app.commit_logs)
      def versions_repo = new File(Holders.config.app.version_repo)
      def opt_repo = new File(Holders.config.app.opt_repo)
      
      if (!commits_repo.exists())
      {
         throw new FileNotFoundException("File ${commits_repo.absolutePath} doesn't exists")
      }
      if (!versions_repo.exists())
      {
         throw new FileNotFoundException("File ${versions_repo.absolutePath} doesn't exists")
      }
      if (!opt_repo.exists())
      {
         throw new FileNotFoundException("File ${opt_repo.absolutePath} doesn't exists")
      }
      // /file system checks
      
      
      // Define server timezone
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
      
      // Used by query builder, all return String
      String.metaClass.asSQLValue = { operand ->
        if (operand == 'contains') return "'%"+ delegate +"%'" // Contains is translated to LIKE, we need the %
        return "'"+ delegate +"'"
      }
      Double.metaClass.asSQLValue = { operand ->
        return delegate.toString()
      }
      Integer.metaClass.asSQLValue = { operand ->
        return delegate.toString()
      }
      Long.metaClass.asSQLValue = { operand ->
        return delegate.toString()
      }
      Date.metaClass.asSQLValue = { operand ->
        def formatterDateDB = new java.text.SimpleDateFormat( Holders.config.app.l10n.db_datetime_format )
        return "'"+ formatterDateDB.format( delegate ) +"'" 
      }
      Boolean.metaClass.asSQLValue = { operand ->
        return delegate.toString()
      }
     
      // call String.randomNumeric(5)
      String.metaClass.static.randomNumeric = { digits ->
        def alphabet = ['0','1','2','3','4','5','6','7','8','9']
        new Random().with {
          (1..digits).collect { alphabet[ nextInt( alphabet.size() ) ] }.join()
        }
      }
      
      String.metaClass.static.random = { n ->
        def alphabet = 'a'..'z'
        new Random().with {
          (1..n).collect { alphabet[ nextInt( alphabet.size() ) ] }.join()
        }
      }

      String.metaClass.static.uuid = { ->
         java.util.UUID.randomUUID() as String
      }
      
      // adds trailing path separator to a file path if it doesnt have it
      String.metaClass.withTrailSeparator = {
         def PS = System.getProperty("file.separator")
         if (!delegate.endsWith(PS)) delegate += PS
         return delegate
      }
     
      // --------------------------------------------------------------------
     

     JSON.registerObjectMarshaller(OperationalTemplateIndex) { opt ->
        return [templateId:  opt.templateId,
                concept:     opt.concept,
                language:    opt.language,
                uid:         opt.uid,
                archetypeId: opt.archetypeId,
                archetypeConcept: opt.archetypeConcept,
                isPublic:    opt.isPublic]
     }
     
     XML.registerObjectMarshaller(OperationalTemplateIndex) { opt, xml ->
        xml.build {
          templateId(opt.templateId)
          concept(opt.concept)
          language(opt.language)
          uid(opt.uid)
          archetypeId(opt.archetypeId)
          archetypeConcept(opt.archetypeConcept)
          isPublic(opt.isPublic)
        }
     }
     


     // Marshallers
     JSON.registerObjectMarshaller(Date) {
        //println "JSON DATE MARSHAL"
        return it?.format(Holders.config.app.l10n.db_datetime_format)
     }
     
     // These for XML dont seem to work...
     XML.registerObjectMarshaller(Date) {
        //println "XML DATE MARSHAL"
        return it?.format(Holders.config.app.l10n.db_datetime_format)
     }
     
     JSON.registerObjectMarshaller(CompositionIndex) { composition ->
        return [uid: composition.uid,
                category: composition.category,
                startTime: composition.startTime,
                subjectId: composition.subjectId,
                ehrUid: composition.ehrUid,
                templateId: composition.templateId,
                archetypeId: composition.archetypeId,
                lastVersion: composition.lastVersion,
                organizationUid: composition.organizationUid,
                parent: composition.getParent().uid
               ]
     }
     
     XML.registerObjectMarshaller(CompositionIndex) { composition, xml ->
        xml.build {
          uid(composition.uid)
          category(composition.category)
          startTime(composition.startTime)
          subjectId(composition.subjectId)
          ehrUid(composition.ehrUid)
          templateId(composition.templateId)
          archetypeId(composition.archetypeId)
          lastVersion(composition.lastVersion)
          organizationUid(composition.organizationUid)
          parent(composition.getParent().uid)
        }
     }
     
     
     JSON.registerObjectMarshaller(DoctorProxy) { doctor ->
        return [namespace: doctor.namespace,
                type: doctor.type,
                value: doctor.value,
                name: doctor.name
               ]
     }
     
     JSON.registerObjectMarshaller(AuditDetails) { audit ->
        def a = [timeCommitted: audit.timeCommitted,
                 committer: audit.committer, // DoctorProxy
                 systemId: audit.systemId
                ]
        // audit for contributions have changeType null, so we avoid to add it here if it is null
        if (audit.changeType) a << [changeType: audit.changeType]
        return a
     }
     
     JSON.registerObjectMarshaller(Contribution) { contribution ->
        return [uid: contribution.uid,
                organizationUid: contribution.organizationUid,
                ehrUid: contribution.ehr.uid,
                versions: contribution.versions.uid, // list of uids
                audit: contribution.audit // AuditDetails
               ]
     }
     
     XML.registerObjectMarshaller(DoctorProxy) { doctor, xml ->
        xml.build {
          namespace(doctor.namespace)
          type(doctor.type)
          value(doctor.value)
          name(doctor.name)
        }
     }
     
     XML.registerObjectMarshaller(AuditDetails) { audit, xml ->
        xml.build {
          timeCommitted(audit.timeCommitted)
          committer(audit.committer) // DoctorProxy
          systemId(audit.systemId)
          if (audit.changeType) changeType(audit.changeType)
        }
     }
     
     XML.registerObjectMarshaller(Contribution) { contribution, xml ->
        xml.build {
          uid(contribution.uid)
          organizationUid(contribution.organizationUid)
          ehrUid(contribution.ehr.uid)
          /*
           * <versions>
           *  <string>8b68a18c-bcb1... </string>
           * </versions>
           */
          //versions(contribution.versions.uid) // list of uids
          /* doesnt work, see below!
          versions {
             contribution.versions.uid.each { _vuid ->
                uid(_vuid)
             }
          }
          */
          audit(contribution.audit) // AuditDetails
        }
        
        // works!
        // https://jwicz.wordpress.com/2011/07/11/grails-custom-xml-marshaller/
        // http://docs.grails.org/2.5.3/api/grails/converters/XML.html
         /*
          * <versions>
          *  <uid>8b68a18c-bcb1... </uid>
          * </versions>
          */
        xml.startNode 'versions'
        contribution.versions.uid.each { _vuid ->
           xml.startNode 'uid'
           xml.chars _vuid
           xml.end()
        }
        xml.end()
     }
     
     
     JSON.registerObjectMarshaller(Organization) { o ->
        return [uid: o.uid,
                name: o.name,
                number: o.number
               ]
     }
     
     XML.registerObjectMarshaller(Organization) { o, xml ->
        xml.build {
          uid(o.uid)
          name(o.name)
          number(o.number)
        }
     }
     
     JSON.registerObjectMarshaller(User) { u ->
        return [username: u.username,
                email: u.email,
                organizations: u.organizations
               ]
     }
     
     XML.registerObjectMarshaller(User) { u, xml ->
        xml.build {
          username(u.username)
          email(u.email)
          xml.startNode 'organizations'
          
             xml.convertAnother u.organizations
   
          xml.end()
        }
     }
     
     
     JSON.registerObjectMarshaller(Query) { q ->
        def j = [uid: q.uid,
                 name: q.name,
                 format: q.format,
                 type: q.type,
                 author: q.author
                ]
        
        if (q.type == 'composition')
        {
           j << [criteriaLogic: q.criteriaLogic]
           j << [templateId:    q.templateId]
           j << [criteria:      q.where.collect { [archetypeId: it.archetypeId, path: it.path, conditions: it.getCriteriaMap()] }]
        }
        else
        {
           j << [group:         q.group] // Group is only for datavalue
           j << [projections:   q.select.collect { [archetypeId: it.archetypeId, path: it.path, rmTypeName: it.rmTypeName] }]
        }
        
        return j
     }
     
     XML.registerObjectMarshaller(Query) { q, xml ->
        xml.build {
          uid(q.uid)
          name(q.name)
          format(q.format)
          type(q.type)
          author(q.author)
        }
        
        if (q.type == 'composition')
        {
           xml.startNode 'criteriaLogic'
              xml.chars (q.criteriaLogic ?: '')
           xml.end()
           xml.startNode 'templateId'
              xml.chars (q.templateId ?: '') // fails if null!
           xml.end()
           
           def criteriaMap
           def _value
           //q.where.each { criteria -> // with this the criteria clases are marshalled twice, it seems the each is returning the criteria instead of just processing the xml format creation.
           for (criteria in q.where) // works ok, so we need to avoid .each
           {
              criteriaMap = criteria.getCriteriaMap() // [attr: [operand: value]] value can be a list
              
              xml.startNode 'criteria'
                 xml.startNode 'archetypeId'
                    xml.chars criteria.archetypeId
                 xml.end()
                 xml.startNode 'path'
                    xml.chars criteria.path
                 xml.end()
                 xml.startNode 'conditions'
 
                    criteriaMap.each { attr, cond ->
                    
                       _value = cond.find{true}.value // can be a list, string, boolean, ...
                       
                       xml.startNode "$attr"
                          xml.startNode 'operand'
                             xml.chars cond.find{true}.key
                          xml.end()
                          
                          if (_value instanceof List)
                          {
                             xml.startNode 'list'
                                _value.each { val ->
                                   
                                   if (val instanceof Date)
                                   {
                                      // FIXME: should use the XML date marshaller
                                      xml.startNode 'item'
                                         xml.chars val.format(Holders.config.app.l10n.ext_datetime_utcformat_nof, TimeZone.getTimeZone("UTC"))
                                      xml.end()
                                   }
                                   else
                                   {
                                      xml.startNode 'item'
                                         xml.chars val.toString() // chars fails if type is Double or other non string
                                      xml.end()
                                   }
                                }
                             xml.end()
                          }
                          else
                          {
                             xml.startNode 'value'
                                xml.chars _value.toString() // chars fails if type is Double or other non string
                             xml.end()
                          }
                       xml.end()
                    }
                 xml.end()
              xml.end()
           }
        }
        else
        {
           xml.startNode 'group'
              xml.chars q.group
           xml.end()
           
           q.select.each { proj ->
              xml.startNode 'projection'
                xml.startNode 'archetypeId'
                  xml.chars proj.archetypeId
                xml.end()
                xml.startNode 'path'
                  xml.chars proj.path
                xml.end()
              xml.end()
           }
        }
     }
     

     JSON.registerObjectMarshaller(Ehr) { ehr ->
        return [uid: ehr.uid,
                dateCreated: ehr.dateCreated,
                subjectUid: ehr.subject.value,
                systemId: ehr.systemId,
                organizationUid: ehr.organizationUid
               ]
     }
     
     XML.registerObjectMarshaller(Ehr) { ehr, xml ->
        xml.build {
          uid(ehr.uid)
          dateCreated(ehr.dateCreated)
          subjectUid(ehr.subject.value)
          systemId(ehr.systemId)
          organizationUid(ehr.organizationUid)
        }
     }
     
     /*
     XML.registerObjectMarshaller(new NameAwareMarshaller() {
        @Override
        public boolean supports(java.lang.Object object) {
           return (object instanceof PaginatedResults)
        }

        @Override
        String getElementName(java.lang.Object o) {
           'result'
        }
     })
     */
     
     JSON.registerObjectMarshaller(PaginatedResults) { pres ->
        
        pres.update() // updates and checks pagination values
        
        def res = [:]
        
        if (pres.list)
           res["${pres.listName}"] = (pres.list ?: []) // prevents null on the json
        else
           res["${pres.listName}"] = (pres.map ?: [:])
        
        res.pagination = [
           'max': pres.max,
           'offset': pres.offset,
           nextOffset: pres.nextOffset, // TODO: verificar que si la cantidad actual es menor que max, el nextoffset debe ser igual al offset
           prevOffset: pres.prevOffset
        ]
        
        if (pres.timing != null) res.timing = pres.timing.toString() + ' ms'
           
        return res
     }
     
     XML.registerObjectMarshaller(PaginatedResults) { pres, xml ->
        
        pres.update() // updates and checks pagination values
        
        // Our list marshaller to customize the name
        xml.startNode pres.listName
           
           if (pres.list)
              xml.convertAnother (pres.list ?: []) // this works, generates "ehr" nodes
           else
              xml.convertAnother (pres.map ?: [:])
           
           /* doesnt generate the patient root, trying with ^
           pres.list.each { item ->
              xml.convertAnother item // marshaller fot the item type should be declared
           }
           */

        xml.end()
        
        xml.startNode 'pagination'
           xml.startNode 'max'
           xml.chars pres.max.toString() // integer fails for .chars
           xml.end()
           xml.startNode 'offset'
           xml.chars pres.offset.toString()
           xml.end()
           xml.startNode 'nextOffset'
           xml.chars pres.nextOffset.toString()
           xml.end()
           xml.startNode 'prevOffset'
           xml.chars pres.prevOffset.toString()
           xml.end()
        xml.end()
        
        // TODO: timing
     }
     
     
     //****** SECURITY *******
     
     // Register custom auth filter
     // ref: https://objectpartners.com/2013/07/11/custom-authentication-with-the-grails-spring-security-core-plugin/
     // See 'authFilter' in grails-app/conf/spring/resources.groovy
     // ref: http://grails-plugins.github.io/grails-spring-security-core/guide/filters.html
     SpringSecurityUtils.clientRegisterFilter('authFilter', SecurityFilterPosition.SECURITY_CONTEXT_FILTER.order + 10)

     
     // Permissions
     if (RequestMap.count() == 0)
     {
        for (String url in [
         '/', // redirects to login, see UrlMappings
         '/error', '/index', '/index.gsp', '/**/favicon.ico', '/shutdown',
         '/assets/**', '/**/js/**', '/**/css/**', '/**/images/**', '/**/fonts/**',
         '/login', '/login.*', '/login/*',
         '/logout', '/logout.*', '/logout/*',
         '/user/register', '/user/resetPassword', '/user/forgotPassword', '/user/registerOk',
         '/simpleCaptcha/**',
         '/j_spring_security_logout',
         '/api/**', // REST security is handled by stateless security plugin
         '/ehr/showCompositionUI', // will be added as a rest service via url mapping
         '/user/profile',
         '/mgt/**' // management api
        ])
        {
            new RequestMap(url: url, configAttribute: 'permitAll').save()
        }
       
        // sections        
        new RequestMap(url: '/notification/**',              configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        
        new RequestMap(url: '/app/index',                    configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
       
        new RequestMap(url: '/ehr/**',                       configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/versionedComposition/**',      configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/contribution/**',              configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/folder/**',                    configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/query/**',                     configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/operationalTemplateIndexItem/**', configAttribute: 'ROLE_ADMIN').save()
        new RequestMap(url: '/archetypeIndexItem/**',        configAttribute: 'ROLE_ADMIN').save()
        new RequestMap(url: '/compositionIndex/**',          configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/operationalTemplate/**',       configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        
        
        // the rest of the operations should be open and security is checked inside the action
        new RequestMap(url: '/user/index',                   configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/user/show/**',                 configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/user/edit/**',                 configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/user/update/**',               configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/user/create',                  configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/user/save',                    configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/user/delete',                  configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/user/resetPasswordRequest/**', configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        
        new RequestMap(url: '/role/**',                      configAttribute: 'ROLE_ADMIN').save()
        new RequestMap(url: '/organization/**',              configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        
        // share/unshare queries and opts between orgs
        new RequestMap(url: '/resource/**',                  configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        
        new RequestMap(url: '/stats/**',                     configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/logs/**',                      configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()

        new RequestMap(url: '/j_spring_security_switch_user', configAttribute: 'ROLE_SWITCH_USER,isFullyAuthenticated()').save()
        
        new RequestMap(url: '/rest/queryCompositions',       configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
        new RequestMap(url: '/rest/queryData',               configAttribute: 'ROLE_ADMIN,ROLE_ORG_MANAGER,ROLE_ACCOUNT_MANAGER').save()
     }
     
     //println Environment.current.toString() +" "+ Environment.TEST.toString() + " <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
     
     // Do not create data if testing, tests will create their own data.
     if (Environment.current != Environment.TEST)
     {
        def organizations = []
        if (Organization.count() == 0)
        {
           println "Creating sample organization"
           
           // Sample organizations
           organizations << new Organization(name: 'CaboLabs', number: '123456')
           //organizations << new Organization(name: 'Clinica del Tratamiento del Dolor', number: '6666')
           //organizations << new Organization(name: 'Cirugia Estetica', number: '5555')
           
           organizations.each {
              it.save(failOnError:true, flush:true)
           }
        }
        else organizations = Organization.list()
        
        
        if (Role.count() == 0 )
        {
           // Create roles
           def adminRole          = new Role(authority: Role.AD).save(failOnError: true, flush: true)
           def orgManagerRole     = new Role(authority: Role.OM).save(failOnError: true, flush: true)
           def accountManagerRole = new Role(authority: Role.AM).save(failOnError: true, flush: true)
           def userRole           = new Role(authority: Role.US).save(failOnError: true, flush: true)
        }
        
        if (User.count() == 0)
        {
           println "Creating sample users"
           
           // Create users
           def adminUser = new User(username: 'admin', email: 'pablo.pazos@cabolabs.com', password: 'admin', enabled: true)
           //adminUser.organizations = [organizations[0]]
           adminUser.save(failOnError: true,  flush: true)
           
           def accManUser = new User(username: 'accman', email: 'pablo.swp+accman@gmail.com', password: 'accman', enabled: true)
           //accManUser.organizations = [organizations[0]]
           accManUser.save(failOnError: true,  flush: true)
           
           def orgManUser = new User(username: 'orgman', email: 'pablo.swp+orgman@gmail.com', password: 'orgman', enabled: true)
           //orgManUser.organizations = [organizations[0]]
           orgManUser.save(failOnError: true,  flush: true)
           
           def user = new User(username: 'user', email: 'pablo.swp+user@gmail.com', password: 'user', enabled: true)
           //user.organizations = [organizations[0]]
           user.save(failOnError: true,  flush: true)
           

           // Associate roles
           UserRole.create( adminUser,  (Role.findByAuthority(Role.AD)), organizations[0], true )
           UserRole.create( accManUser, (Role.findByAuthority(Role.AM)), organizations[0], true )
           UserRole.create( orgManUser, (Role.findByAuthority(Role.OM)), organizations[0], true )
           UserRole.create( user,       (Role.findByAuthority(Role.US)), organizations[0], true )
        }
        
        
        log.debug( 'Current working dir: '+ new File(".").getAbsolutePath() ) // Current working directory
        
        
        // Always regenerate indexes in deploy
        if (OperationalTemplateIndex.count() == 0)
        {
           println "Indexing Operational Templates"
           
           def ti = new com.cabolabs.archetype.OperationalTemplateIndexer()
           ti.setupBaseOpts()
           ti.indexAll( Organization.get(1) )
        }
        
        // TODO: because initially there are no shares, the indexAll 
        //       wont share the OPTs with the org, so we do it manually here.
        
        // OPT loading
        def optMan = OptManager.getInstance( Holders.config.app.opt_repo.withTrailSeparator() )
        optMan.unloadAll()
        optMan.loadAll()
     
     } // not TEST ENV
     
     

     /*
     // Sample EHRs for testing purposes
     if (Ehr.count() == 0)
     {
        def ehr_subject_uids = [
           '11111111-1111-1111-1111-111111111111',
           '22222222-1111-1111-1111-111111111111',
           '33333333-1111-1111-1111-111111111111',
           '44444444-1111-1111-1111-111111111111',
           '55555555-1111-1111-1111-111111111111'
        ]
        
        
        def ehr
        def c = Organization.count()
        
        ehr_subject_uids.eachWithIndex { uid, i ->
           ehr = new Ehr(
              uid: uid, // the ehr id is the same as the patient just to simplify testing
              subject: new PatientProxy(
                 value: uid
              ),
              organizationUid: Organization.get(i % c + 1).uid
           )
         
           if (!ehr.save()) println ehr.errors
        }
     }
     */
     
     
      // Create plans
      def p1
      if (Plan.count() == 0)
      {
         // Create plans
         p1 = new Plan(
           name: "Free Educational",
           maxTransactions: 50,
           maxDocuments: 100,
           repositorySize: 1024*15*120, // allows 120 documents of 15KB
           totalRepositorySize: 1024*15*120*12, // monthly size * 12 months
           period: Plan.periods.MONTHLY
         )
        
         p1.save(failOnError: true)
      }
      else
      {
         p1 = Plan.get(1)
      }
     
      // Associate free plans by default
      def orgs = Organization.list()
      orgs.each { org ->
         if (!PlanAssociation.findByOrganizationUid(org.uid))
         {
            p1.associate( org )
         }
      }
      
     
     
      // ============================================================
      // migration for latest changes
      /*
      def versionsss = Version.list()
      def version_file, commit_file
      versionsss.each {
         if (!it.fileUid)
         {
            it.fileUid = java.util.UUID.randomUUID() as String
            if (!it.save())
            {
               println it.errors
            }
            else
            {
               // update the version file names
               version_file = new File(Holders.config.app.version_repo + it.uid.replaceAll('::', '_') +'.xml')
               if (version_file.exists())
               {
                  version_file.renameTo( Holders.config.app.version_repo + it.fileUid +'.xml' )
               }
               else
                  println "file doesnt exists "+ version_file.path
            }
         }
      }
      
      
      def commitsss = Commit.list()
      commitsss.each {
         if (!it.fileUid)
         {
            it.fileUid = java.util.UUID.randomUUID() as String
            if (!it.save())
            {
               println it.errors
            }
            else
            {
               commit_file = new File(Holders.config.app.commit_logs + it.id.toString() +'.xml')
               if (commit_file.exists())
               {
                  commit_file.renameTo( Holders.config.app.commit_logs + it.fileUid +'.xml' )
               }
               else
                  println "file doesnt exists "+ commit_file.path
            }
         }
      }
      */
      /* needed for 0.9 - 0.9.5
      // Fill rm_type_name for data_value_index for old indexes
      def aii
      com.cabolabs.ehrserver.ehr.clinical_documents.data.DataValueIndex.list().each { dvi ->
         println dvi.archetypeId+" "+dvi.archetypePath
         aii = ArchetypeIndexItem.findByArchetypeIdAndPath(dvi.archetypeId, dvi.archetypePath)
         println "aii " + aii
         if (aii)
         {
         dvi.rmTypeName = aii.rmTypeName
         dvi.save()
         }
         else
         {
            println "not aii!!!"
         }
      }
     */
     
     /*
     // Test notifications
     def notifs = [
        new Notification(name:'notif 1', language:'en', text:'Look at me!'),
        new Notification(name:'notif 2', language:'en', text:'Look at me!', forSection:'ehr'),
        new Notification(name:'notif 3', language:'en', text:'Look at me!', forOrganization:Organization.get(1).uid),
        new Notification(name:'notif 4', language:'en', text:'Look at me!', forUser:1),
        new Notification(name:'notif 5', language:'en', text:'Look at me!', forSection:'query', forOrganization:Organization.get(1).uid),
        new Notification(name:'notif 6', language:'en', text:'Look at me!', forSection:'query', forOrganization:Organization.get(1).uid, forUser:1),
        
        new Notification(name:'notif 7', language:'es', text:'mirame!'),
        new Notification(name:'notif 8', language:'es', text:'mirame', forSection:'ehr'),
        new Notification(name:'notif 9', language:'es', text:'mirame!', forOrganization:Organization.get(1).uid),
        new Notification(name:'notif 10', language:'es', text:'mirame!', forUser:1)
     ]
     
     def statuses = []
     notifs.each { notif ->
        if (!notif.forUser)
        {
           User.list().each { user ->
              statuses << new NotificationStatus(user:user, notification:notif)
           }
        }
        else
        {
           statuses << new NotificationStatus(user:User.get(notif.forUser), notification:notif)
        }
        
        notif.save(failOnError: true)
     }
     
     statuses.each { status ->
        status.save(failOnError: true)
     }
     */
      
      /*
      com.cabolabs.ehrserver.ehr.clinical_documents.data.DataValueIndex.list().each {
         it.delete()
      }
      CompositionIndex.list().each {
         it.dataIndexed = false
         it.save()
      }
      */
      
      /*
      QueryShare.list().each {
         it.delete()
      }
      Query.list().each {
         it.delete()
      }
      DataGet.list().each {
         it.delete()
      }
      DataCriteria.list().each {
         it.delete()
      }
      */
   }
   
   def destroy = {
   }
}
