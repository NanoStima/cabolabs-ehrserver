package com.cabolabs.ehrserver.account

import com.cabolabs.security.*
import grails.test.spock.IntegrationSpec

class StatsControllerIntegrationSpec extends IntegrationSpec {

   StatsController controller = new StatsController()
   
   private String orgUid = '11111111-1111-1111-1111-111111111178'
   
   private createOrganization()
   {
      println "NEW ORGANIZATION StatsController"
      def org = new Organization(uid: orgUid, name: 'CaboLabs', number: '123456')
      org.save(failOnError: true)
   }
   
   private createUsersAndRoles()
   {
      def adminRole          = new Role(authority: Role.AD).save(failOnError: true, flush: true)
      def accountManagerRole = new Role(authority: Role.AM).save(failOnError: true, flush: true)

           
      def user = new User(
         username: 'testadmin', password: 'testadmin',
         email: 'testadmin@domain.com').save(failOnError:true, flush: true)
      
      UserRole.create( user, adminRole, Organization.findByUid(orgUid), true )
      
      
      user = new User(
         username: 'accman', password: 'accman',
         email: 'accman@domain.com').save(failOnError:true, flush: true)
      
      UserRole.create( user, accountManagerRole, Organization.findByUid(orgUid), true )
   }
   
   
   def setup()
   {
      createOrganization()
      createUsersAndRoles()
   }

   def cleanup()
   {
      def org = Organization.findByUid(orgUid)
      def user = User.findByUsername("testadmin")
      def role = Role.findByAuthority(Role.AD)
      
      UserRole.remove(user, role, org)
      user.delete(flush: true)
      
      user = User.findByUsername("accman")
      role = Role.findByAuthority(Role.AM)
      
      UserRole.remove(user, role, org)
      user.delete(flush: true)

      org.delete(flush: true)
   }

   void "account stats"()
   {
      setup:
         // setup rest token user
         controller.request.securityStatelessMap = [username: 'testadmin', extradata:[organization:'123456', org_uid:orgUid]]
      
      when:
         def model = controller.userAccountStats(username)
         
      then:
         //println controller.response.text
         controller.response.json.organizations.keySet()[0] == orgUid
         controller.response.status == status
         
      where:
         username = 'accman'
         status = 200
         /* cant test other cases because the filter mock doesnt inject the springsecurityservice and the test fails because of that
         ''       | 400
         'orgman' | 400
         'accman' | 200
         */
   }
}
