package org.cloud.user.repository;

import org.babyfish.jimmer.spring.repo.support.AbstractJavaRepository;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.cloud.user.dto.UserSearchView;
import org.cloud.user.dto.UserView;
import org.cloud.user.entity.User;
import org.cloud.user.entity.UserDraft;
import org.cloud.user.entity.UserFetcher;
import org.cloud.user.entity.UserTable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class UserRepository extends AbstractJavaRepository<User, UUID> {
    private static final UserTable table = UserTable.$;

    public UserRepository(JSqlClient sql) {
        super(sql);
    }

    public User createUser(String email, String password) {
        long now = System.currentTimeMillis();

        // 构建用户对象
        User user = UserDraft.$.produce(
                draft -> {
                    draft.setEmail(email);
                    draft.setUsername("user_" + UUID.randomUUID().toString().substring(0, 8));
                    draft.setPwd(password);
                    draft.setCreatedAt(now);
                    draft.setUserStatus("NORMAL");
                    draft.setUserRole("user");
                    draft.setStorageCapacity(5368709120L);
                    draft.setUsedCapacity(0L);
                }
        );

        // 保存用户
        return sql.saveCommand(user)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getModifiedEntity();
    }

    public void initRootDir(UUID userId, UUID rootDir) {
        sql.createUpdate(table)
                .set(table.rootDir(), rootDir)
                .where(table.id().eq(userId))
                .execute();
    }

    public UUID getRootDirectoryId(UUID userId) {
        return sql.createQuery(table)
                .where(table.id().eq(userId))
                .select(table.rootDir())
                .fetchOneOrNull();
    }

    public User getUserByEmail(String email) {
        return sql.createQuery(table)
                .where(table.email().eq(email))
                .select(table.fetch(UserFetcher.$.allScalarFields()))
                .fetchOne();
    }

    public User getUserById(UUID id) {
        return sql.createQuery(table)
                .where(table.id().eq(id))
                .select(table.fetch(UserFetcher.$.allScalarFields()))
                .fetchOne();
    }

    public String getUsernameById(UUID id) {
        return sql.createQuery(table)
                .where(table.id().eq(id))
                .select(table.username())
                .fetchOneOrNull();
    }

    public List<UserSearchView> getUserIdsByName(String key) {
        return sql.createQuery(table)
                .where(table.username().ilike("%" + key + "%"))
                .select(table.fetch(UserSearchView.class))
                .execute();
    }

    public UserView getUserView(UUID id) {
        return sql.findById(UserView.class, id);
    }

    public int updatePassword(String email, String password) {
        return sql.createUpdate(table)
                .set(table.pwd(), password)
                .where(table.email().eq(email))
                .execute();
    }

    public void updateLastLoginAt(UUID id, Long lastLoginAt) {
        sql.createUpdate(table)
                .set(table.lastLoginAt(), lastLoginAt)
                .where(table.id().eq(id))
                .execute();
    }

    public void updateUsedCapacity(UUID id, Long usedCapacity) {
        sql.createUpdate(table)
                .set(table.usedCapacity(), usedCapacity)
                .where(table.id().eq(id))
                .execute();
    }

    public int updateUserInfo(String username, String bio, String avatar, UUID userId) {
        User user = UserDraft.$.produce(
                draft -> {
                    draft.setId(userId);
                    if(username != null) draft.setUsername(username);
                    if(bio != null) draft.setBio(bio);
                    if(avatar != null) draft.setAvatar(avatar);
                }
        );

        return sql.saveCommand(user)
                .setMode(SaveMode.UPDATE_ONLY)
                .execute()
                .getAffectedRowCount(User.class);
    }

    public boolean isEmailExists(String email) {
        return sql.createQuery(table)
                .where(table.email().eq(email))
                .exists();
    }
}
