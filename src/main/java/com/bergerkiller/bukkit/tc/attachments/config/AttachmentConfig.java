package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.utils.ListCallbackCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The configuration of a single attachment. Stores the configuration of the attachment
 * itself, as well as information about its position in the attachment hierarchy. Child
 * attachments are also made available, but should only be used when handling
 * {@link ChangeType#ADDED}
 */
public interface AttachmentConfig {
    /**
     * Gets the parent attachment of this attachment
     *
     * @return parent attachment, null if this is the root attachment
     */
    AttachmentConfig parent();

    /**
     * Gets the child attachment configurations parented to this
     * attachment. This should only be used on the returned configuration of
     * {@link AttachmentConfigTracker#startTracking(AttachmentConfigListener)
     * AttachmentConfigTracker.startTracking()}
     * or when handling {@link ChangeType#ADDED ChangeType.ADDED} events.
     *
     * @return List of child attachment configurations
     */
    List<AttachmentConfig> children();

    /**
     * Tries to find an attachment configuration which is a child of this one.
     * If no such child exists at this index, returns <i>null</i>.
     *
     * @param childIndex Child index, starting at 0
     * @return Child at this index, or <i>null</i> if one doesn't exist
     */
    default AttachmentConfig child(int childIndex) {
        List<AttachmentConfig> children = this.children();
        if (childIndex < 0 || childIndex >= children.size()) {
            return null;
        } else {
            return children.get(childIndex);
        }
    }

    /**
     * Tries to find an attachment configuration which is a recursive child
     * of this one. If no such child exists at this index, returns <i>null</i>.
     * The input child index path is traversed in order. An empty array will
     * return this attachment configuration, while [1] will return the second
     * child.
     *
     * @param childPath Parent-to-child series of child indices
     * @return Child at this child index path, or <i>null</i> if one doesn't exist
     * @see #child(int)
     */
    default AttachmentConfig child(int[] childPath) {
        AttachmentConfig p = this;
        int len = childPath.length;
        if (len > 0) {
            int i = 0;
            do {
                p = p.child(childPath[i]);
            } while (++i < len && p != null);
        }
        return p;
    }

    /**
     * Tries to find an attachment configuration which is a recursive child
     * of this one. If no such child exists at this index, returns <i>null</i>.
     * The input <b>relative</b> {@link YamlPath} is traversed to find the
     * attachment using this configuration. Specifying a key or property of
     * the attachment configuration will also return that attachment.
     *
     * @param path Relative YamlPath leading from this attachment configuration
     *             to the child or a property of the child to find
     * @return child at this path, or <i>null</i> if not found
     */
    default AttachmentConfig child(YamlPath path) {
        if (path.isRoot()) {
            return this;
        }

        // Navigate the children recursively until we find no more child relative
        // to which the path is found.
        YamlLogic logic = YamlLogic.INSTANCE;
        AttachmentConfig currentAttachment = this;
        YamlPath resultPath = path;
        final YamlPath searchPath = logic.join(this.path(), path); // Make absolute
        boolean found;
        do {
            found = false;
            for (AttachmentConfig child : currentAttachment.children()) {
                YamlPath childRelativePath = logic.getRelativePath(child.path(), searchPath);
                if (childRelativePath != null) {
                    currentAttachment = child;
                    resultPath = childRelativePath;
                    found = true;
                    break;
                }
            }
        } while (found);

        // Reached the end. The relative result path that now remains must either be
        // empty (point to the attachment directly) or refer to a property of the attachment.
        // If its first path element is "attachments" then it refers to a child attachment
        // that was not found.
        if (!resultPath.isRoot()) {
            while (!resultPath.parent().isRoot()) {
                resultPath = resultPath.parent();
            }
            if (resultPath.name().equals("attachments")) {
                return null;
            }
        }
        return currentAttachment;
    }

    /**
     * Gets whether this attachment configuration was removed, and is no longer
     * linked or updated. If removed, {@link #config()} should no longer be used
     * as it will be stale. {@link #children()} will remain functional, but those
     * will also have been removed. {@link #path()}, like config(), might no longer
     * be valid.
     *
     * @return True if this attachment configuration was removed
     */
    boolean isRemoved();

    /**
     * Gets the child index of this attachment relative to {@link #parent()}.
     * When an attachment is removed, this is the index relative to the parent
     * that should be removed. When an attachment is added, this is the index
     * where a new attachment should be inserted.
     *
     * @return parent-relative child index
     */
    int childIndex();

    /**
     * Gets a full sequence of parent-child indices that lead from the root
     * attachment to this current attachment. Changes to the root attachment
     * will return an empty array.
     *
     * @return child path
     */
    default int[] childPath() {
        ArrayList<AttachmentConfig> parents = new ArrayList<>(10);
        for (AttachmentConfig a = this; a.parent() != null; a = a.parent()) {
            parents.add(a);
        }
        int[] path = new int[parents.size()];
        for (int i = 0, j = path.length - 1; j >= 0; --j, ++i) {
            path[i] = parents.get(j).childIndex();
        }
        return path;
    }

    /**
     * Gets a root-relative path to this attachment. To get an absolute path,
     * use {@link ConfigurationNode#getYamlPath()} instead.
     *
     * @return root-relative path to this attachment's Yaml configuration
     */
    YamlPath path();

    /**
     * Gets the attachment type identifier. This might be out of sync with
     * {@link #config()} when handling {@link ChangeType#REMOVED}. In that
     * case, this will return the attachment type id that was removed, not
     * the type that might replace it.
     *
     * @return Attachment type identifier
     */
    String typeId();

    /**
     * Gets the current configuration node of this attachment.
     * Should not be used when handling attachment removal, as this
     * configuration might be out of date or contain information about
     * an entirely different attachment. This configuration is only
     * suitable for loading the configuration of this attachment,
     * not for inspecting parent or child attachments.
     *
     * @return attachment configuration
     */
    ConfigurationNode config();

    /**
     * Figures out all live {@link Attachment} instances that use this attachment configuration,
     * and runs an action on them. This runs sync and can only be used from the main thread.<br>
     * <br>
     * It is permitted to collect all live attachments and do something with them later.
     * Polling this information will then be required to stay up-to-date.<br>
     * <br>
     * If this attachment is a model configuration, then all live trains that use this model
     * in their configuration will run the action as well. As such, this might run for
     * more than one live attachment in that case.<br>
     * <br>
     * Errors that occur inside the callback are logged, but will not pass back to the caller.
     * Ideally the callback uses proper error handling.
     *
     * @param action Action to run for all live attachments using this configuration
     * @throws IllegalStateException If this attachment configuration was previously removed
     */
    void runAction(Consumer<Attachment> action);

    /**
     * Finds all live {@link Attachment} instances that use this attachment configuration
     *
     * @return Unmodifiable List of live Attachments
     * @see #runAction(Consumer)
     */
    default List<Attachment> liveAttachments() {
        ListCallbackCollector<Attachment> collector = new ListCallbackCollector<>();
        runAction(collector);
        return collector.result();
    }

    /**
     * Attachment Configuration for a Model Attachment that has a valid model name
     * defined. Model name will not change and will always be non-empty. If changes
     * happen a new Model configuration is created, and if empty, a normal
     * AttachmentConfig is created instead.
     */
    interface Model extends AttachmentConfig {

        /**
         * Gets the name of the model this model attachment uses. Invisibly, the
         * attachments of this model configuration are mixed in.
         *
         * @return model name. Is never empty.
         */
        String modelName();
    }

    /**
     * A single attachment Change notification
     */
    final class Change {
        private final ChangeType changeType;
        private final AttachmentConfig attachment;

        public Change(ChangeType changeType, AttachmentConfig attachment) {
            this.changeType = changeType;
            this.attachment = attachment;
        }

        /**
         * Gets the attachment that was removed, created or changed.
         * Use {@link AttachmentConfig#childIndex()} to figure out in your own
         * representation what or where to remove/create/find the attachment.
         *
         * @return attachment
         */
        public AttachmentConfig attachment() {
            return attachment;
        }

        /**
         * Gets the type of change that occurred
         *
         * @return change type
         */
        public ChangeType changeType() {
            return changeType;
        }

        @Override
        public String toString() {
            return "{" + changeType.name() + " " + attachment.path() + "}";
        }
    }

    /**
     * A type of change that occurred to an attachment
     */
    enum ChangeType {
        /** The attachment and all its children were added */
        ADDED(AttachmentConfigListener::onAttachmentAdded),
        /** The attachment and all its children were removed */
        REMOVED(AttachmentConfigListener::onAttachmentRemoved),
        /**
         * The attachment configuration changed and needs to be re-loaded.
         * The {@link AttachmentConfig} instance will not have changed
         * since previous events.
         */
        CHANGED(AttachmentConfigListener::onAttachmentChanged),
        /**
         * All attachments have been synchronized, and the specified root
         * attachment stores an up-to-date representation of the current
         * attachment configuration.
         */
        SYNCHRONIZED(AttachmentConfigListener::onSynchronized);

        private final BiConsumer<AttachmentConfigListener, AttachmentConfig> callback;

        ChangeType(BiConsumer<AttachmentConfigListener, AttachmentConfig> callback) {
            this.callback = callback;
        }

        public BiConsumer<AttachmentConfigListener, AttachmentConfig> callback() {
            return callback;
        }
    }
}
