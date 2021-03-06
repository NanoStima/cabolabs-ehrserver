
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

package com.cabolabs.ehrserver

import com.cabolabs.ehrserver.ehr.clinical_documents.OperationalTemplateIndex
import com.cabolabs.ehrserver.ehr.clinical_documents.OperationalTemplateIndexShare
import com.cabolabs.ehrserver.query.Query
import com.cabolabs.ehrserver.query.QueryShare
import com.cabolabs.security.Organization

import grails.transaction.Transactional

@Transactional
class ResourceService {

   /**
    * Creates a share of the query with the organization.
    * @param query
    * @param organization
    * @return
    */
   def shareQuery(Query query, Organization organization)
   {
      if (QueryShare.countByQueryAndOrganization(query, organization) == 0)
      {
         def share = new QueryShare(query: query, organization: organization)
         share.save(failOnError: true)
      }
   }
   
   /**
    * Clean all the shares of a query.
    * @param query
    * @return
    */
   def cleanSharesQuery(Query query)
   {
      def shares = QueryShare.findAllByQuery(query)
      shares.each { share ->
         share?.delete(failOnError: true)
      }
   }
   
   /**
    * Cleans all the shares of a query, except for one organization.
    * @param query
    * @param organization
    * @return
    */
   def cleanSharesQueryBut(Query query, Organization organization)
   {
      def shares = QueryShare.findAllByQuery(query)
      shares.each { share ->
         if (share.organization.id != organization.id)
            share?.delete(failOnError: true)
      }
   }
   
   /**
    * Creates a share of the opt with the organization.
    * @param opt
    * @param organization
    * @return
    */
   def shareOpt(OperationalTemplateIndex opt, Organization organization)
   {
      if (OperationalTemplateIndexShare.countByOptAndOrganization(opt, organization) == 0)
      {
         def share = new OperationalTemplateIndexShare(opt: opt, organization: organization)
         share.save(failOnError: true)
      }
   }
   
   /**
    * Clean all the shares of an opt.
    * @param opt
    * @return
    */
   def cleanSharesOpt(OperationalTemplateIndex opt)
   {
      def shares = OperationalTemplateIndexShare.findAllByOpt(opt)
      shares.each { share ->
         share?.delete(failOnError: true)
      }
   }
   
   /**
    * Cleans all the shares of an opt, except for one organization.
    * @param opt
    * @param organization
    * @return
    */
   def cleanSharesOptBut(OperationalTemplateIndex opt, Organization organization)
   {
      def shares = OperationalTemplateIndexShare.findAllByOpt(opt)
      shares.each { share ->
         if (share.organization.id != organization.id)
            share?.delete(failOnError: true)
      }
   }
}
