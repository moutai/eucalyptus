/*
 * Software License Agreement (BSD License)
 *
 * Copyright (c) 2008, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 *
 * * Redistributions of source code must retain the above
 *   copyright notice, this list of conditions and the
 *   following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the
 *   following disclaimer in the documentation and/or other
 *   materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * Author: Sunil Soman sunils@cs.ucsb.edu
 */

package edu.ucsb.eucalyptus.cloud.entities;

import edu.ucsb.eucalyptus.msgs.AccessControlListType;
import edu.ucsb.eucalyptus.msgs.Grant;
import edu.ucsb.eucalyptus.msgs.Grantee;
import edu.ucsb.eucalyptus.msgs.Group;
import edu.ucsb.eucalyptus.util.UserManagement;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.eucalyptus.ws.util.WalrusProperties;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table( name = "Buckets" )
@Cache( usage = CacheConcurrencyStrategy.READ_WRITE )
public class BucketInfo {
    @Id
    @GeneratedValue
    @Column( name = "bucket_id" )
    private Long id = -1l;

    @Column( name = "owner_id" )
    private String ownerId;

    @Column( name = "bucket_name" )
    private String bucketName;

    @Column( name = "bucket_creation_date" )
    private Date creationDate;

    @Column(name="global_read")
    private Boolean globalRead;

    @Column(name="global_write")
    private Boolean globalWrite;

    @Column(name="global_read_acp")
    private Boolean globalReadACP;

    @Column(name="global_write_acp")
    private Boolean globalWriteACP;

    @Column(name="bucket_size")
    private Long bucketSize;

    @Column(name="bucket_location")
    private String location;

    @OneToMany( cascade = CascadeType.ALL )
    @JoinTable(
            name = "bucket_has_grants",
            joinColumns = { @JoinColumn( name = "bucket_id" ) },
            inverseJoinColumns = @JoinColumn( name = "grant_id" )
    )
    @Cache( usage = CacheConcurrencyStrategy.READ_WRITE )
    private List<GrantInfo> grants = new ArrayList<GrantInfo>();

    public BucketInfo() {
    }

    public BucketInfo(String bucketName) {
        this.bucketName = bucketName;
    }

    public BucketInfo(String ownerId, String bucketName, Date creationDate) {
        this.ownerId = ownerId;
        this.bucketName = bucketName;
        this.creationDate = creationDate;
    }

    public String getBucketName()
    {
        return this.bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Date getCreationDate() {
        return creationDate;
    }
    public boolean isGlobalRead() {
        return globalRead;
    }

    public void setGlobalRead(Boolean globalRead) {
        this.globalRead = globalRead;
    }

    public boolean isGlobalWrite() {
        return globalWrite;
    }

    public void setGlobalWrite(Boolean globalWrite) {
        this.globalWrite = globalWrite;
    }

    public boolean isGlobalReadACP() {
        return globalReadACP;
    }

    public void setGlobalReadACP(Boolean globalReadACP) {
        this.globalReadACP = globalReadACP;
    }

    public boolean isGlobalWriteACP() {
        return globalWriteACP;
    }

    public void setGlobalWriteACP(Boolean globalWriteACP) {
        this.globalWriteACP = globalWriteACP;
    }

    public List<GrantInfo> getGrants() {
        return grants;
    }

    public void setGrants(List<GrantInfo> grants) {
        this.grants = grants;
    }

    public boolean canWrite(String userId) {
        if (globalWrite) {
            return true;
        }

        for (GrantInfo grantInfo: grants) {
            if (grantInfo.getUserId().equals(userId)) {
                if (grantInfo.isWrite()) {
                    return true;
                }
            }
        }
        if(UserManagement.isAdministrator(userId)) {
            return true;
        }

        return false;
    }

    public boolean canRead(String userId) {
        if (globalRead) {
            return true;
        }

        for (GrantInfo grantInfo: grants) {
            if(grantInfo.getGrantGroup() != null) {
                String groupUri = grantInfo.getGrantGroup();
                if(groupUri.equals(WalrusProperties.AUTHENTICATED_USERS_GROUP))
                    return true;
            }

        }

        for (GrantInfo grantInfo: grants) {
            if (grantInfo.getUserId().equals(userId)) {
                if (grantInfo.isRead()) {
                    return true;
                }
            }
        }

        if(UserManagement.isAdministrator(userId)) {
            return true;
        }

        return false;
    }

    public boolean canWriteACP(String userId) {
        if (globalWriteACP) {
            return true;
        }

        for (GrantInfo grantInfo: grants) {
            if (grantInfo.getUserId().equals(userId)) {
                if (grantInfo.isWriteACP()) {
                    return true;
                }
            }
        }
        if(UserManagement.isAdministrator(userId)) {
            return true;
        }

        return false;
    }

    public boolean canReadACP(String userId) {
        if(ownerId.equals(userId)) {
            //owner can always acp
            return true;
        } else if (globalReadACP) {
            return true;
        } else {
            for (GrantInfo grantInfo: grants) {
                if(grantInfo.getUserId().equals(userId) && grantInfo.isReadACP()) {
                    return true;
                }
            }
        }
        if(UserManagement.isAdministrator(userId)) {
            return true;
        }
        return false;
    }

    public void resetGlobalGrants() {
        globalRead = globalWrite = globalReadACP = globalWriteACP = false;
    }

    public  void addGrants(String ownerId, List<GrantInfo>grantInfos, AccessControlListType accessControlList) {
        ArrayList<Grant> grants = accessControlList.getGrants();
        Grant foundGrant = null;
        globalRead = globalReadACP = false;
        globalWrite = globalWriteACP = false;
        if (grants.size() > 0) {
            for (Grant grant: grants) {
                String permission = grant.getPermission();
                if (permission.equals("aws-exec-read")) {
                    globalRead = globalReadACP = false;
                    globalWrite = globalWriteACP = false;
                    foundGrant = grant;
                }   else if (permission.equals("public-read")) {
                    globalRead = globalReadACP = true;
                    globalWrite = globalWriteACP = false;
                    foundGrant = grant;
                }   else if (permission.equals("public-read-write")) {
                    globalRead = globalReadACP = true;
                    globalWrite = globalWriteACP = true;
                    foundGrant = grant;
                }   else if (permission.equals("authenticated-read")) {
                    globalRead = globalReadACP = false;
                    globalWrite = globalWriteACP = false;
                    foundGrant = grant;
                } else if(grant.getGrantee().getGroup() != null) {
                    String groupUri = grant.getGrantee().getGroup().getUri();
                    if(groupUri.equals(WalrusProperties.ALL_USERS_GROUP)) {
                        if(permission.equals("FULL_CONTROL"))
                            globalRead = globalReadACP = globalWrite = globalWriteACP = true;
                        else if(permission.equals("READ"))
                            globalRead = true;
                        else if(permission.equals("READ_ACP"))
                            globalReadACP = true;
                        else if(permission.equals("WRITE"))
                            globalWrite = true;
                        else if(permission.equals("WRITE_ACP"))
                            globalWriteACP = true;
                    }
                    foundGrant = grant;
                }
            }
        }
        if(foundGrant != null) {
            grants.remove(foundGrant);
        }
        GrantInfo.addGrants(ownerId, grantInfos, accessControlList);
    }

    public void readPermissions(List<Grant> grants) {
        if(globalRead && globalReadACP && globalWrite && globalWriteACP) {
            grants.add(new Grant(new Grantee(new Group(WalrusProperties.ALL_USERS_GROUP)), "FULL_CONTROL"));
            return;
        }
        if(globalRead) {
            grants.add(new Grant(new Grantee(new Group(WalrusProperties.ALL_USERS_GROUP)), "READ"));
        }
        if(globalReadACP) {
            grants.add(new Grant(new Grantee(new Group(WalrusProperties.ALL_USERS_GROUP)), "READ_ACP"));
        }
        if(globalWrite) {
            grants.add(new Grant(new Grantee(new Group(WalrusProperties.ALL_USERS_GROUP)), "WRITE"));
        }
        if(globalWriteACP) {
            grants.add(new Grant(new Grantee(new Group(WalrusProperties.ALL_USERS_GROUP)), "WRITE_ACP"));
        }
    }

    public Long getBucketSize() {
        return bucketSize;
    }

    public void setBucketSize(Long bucketSize) {
        this.bucketSize = bucketSize;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}