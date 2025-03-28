package org.leavesmc.leaves.protocol.servux.litematics.schematic.selection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.apache.commons.lang3.tuple.Pair;
import org.leavesmc.leaves.protocol.servux.litematics.schematic.placement.SchematicPlacement;
import org.leavesmc.leaves.protocol.servux.litematics.schematic.placement.SubRegionPlacement;
import org.leavesmc.leaves.protocol.servux.litematics.schematic.utils.JsonUtils;
import org.leavesmc.leaves.protocol.servux.litematics.schematic.utils.PositionUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AreaSelection {
    protected final Map<String, Box> subRegionBoxes = new HashMap<>();
    protected String name = "Unnamed";
    protected boolean originSelected;
    protected BlockPos calculatedOrigin = BlockPos.ZERO;
    protected boolean calculatedOriginDirty = true;
    @Nullable
    protected BlockPos explicitOrigin = null;
    @Nullable
    protected String currentBox;

    public static AreaSelection fromPlacement(SchematicPlacement placement) {
        ImmutableMap<String, Box> boxes = placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED);
        BlockPos origin = placement.getOrigin();

        AreaSelection selection = new AreaSelection();
        selection.setExplicitOrigin(origin);
        selection.name = placement.getName();
        selection.subRegionBoxes.putAll(boxes);

        return selection;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected void markDirty() {
        this.calculatedOriginDirty = true;
    }

    @Nullable
    public String getCurrentSubRegionBoxName() {
        return this.currentBox;
    }

    public boolean setSelectedSubRegionBox(@Nullable String name) {
        if (name == null || this.subRegionBoxes.containsKey(name)) {
            this.currentBox = name;
            return true;
        }

        return false;
    }

    public boolean isOriginSelected() {
        return this.originSelected;
    }

    public void setOriginSelected(boolean selected) {
        this.originSelected = selected;
    }

    /**
     * Returns the effective origin point. This is the explicit origin point, if one has been set,
     * otherwise it's an automatically calculated origin point, located at the minimum corner
     * of all the boxes.
     *
     * @return
     */
    public BlockPos getEffectiveOrigin() {
        if (this.explicitOrigin != null) {
            return this.explicitOrigin;
        } else {
            if (this.calculatedOriginDirty) {
                this.updateCalculatedOrigin();
            }

            return this.calculatedOrigin;
        }
    }

    /**
     * Get the explicitly defined origin point, if any.
     *
     * @return
     */
    @Nullable
    public BlockPos getExplicitOrigin() {
        return this.explicitOrigin;
    }

    public void setExplicitOrigin(@Nullable BlockPos origin) {
        this.explicitOrigin = origin;

        if (origin == null) {
            this.originSelected = false;
        }
    }

    protected void updateCalculatedOrigin() {
        Pair<BlockPos, BlockPos> pair = PositionUtils.getEnclosingAreaCorners(this.subRegionBoxes.values());

        if (pair != null) {
            this.calculatedOrigin = pair.getLeft();
        } else {
            this.calculatedOrigin = BlockPos.ZERO;
        }

        this.calculatedOriginDirty = false;
    }

    @Nullable
    public Box getSubRegionBox(String name) {
        return this.subRegionBoxes.get(name);
    }

    @Nullable
    public Box getSelectedSubRegionBox() {
        return this.currentBox != null ? this.subRegionBoxes.get(this.currentBox) : null;
    }

    public Collection<String> getAllSubRegionNames() {
        return this.subRegionBoxes.keySet();
    }

    public List<Box> getAllSubRegionBoxes() {
        return ImmutableList.copyOf(this.subRegionBoxes.values());
    }

    public ImmutableMap<String, Box> getAllSubRegions() {
        ImmutableMap.Builder<String, Box> builder = ImmutableMap.builder();
        builder.putAll(this.subRegionBoxes);
        return builder.build();
    }

    @Nullable
    public String createNewSubRegionBox(BlockPos pos1, final String nameIn) {
        this.clearCurrentSelectedCorner();
        this.setOriginSelected(false);

        String name = nameIn;
        int i = 1;

        while (this.subRegionBoxes.containsKey(name)) {
            name = nameIn + " " + i;
            i++;
        }

        Box box = new Box();
        box.setName(name);
        box.setSelectedCorner(PositionUtils.Corner.CORNER_1);
        this.currentBox = name;
        this.subRegionBoxes.put(name, box);
        this.setSubRegionCornerPos(box, PositionUtils.Corner.CORNER_1, pos1);
        this.setSubRegionCornerPos(box, PositionUtils.Corner.CORNER_2, pos1);

        return name;
    }

    public void clearCurrentSelectedCorner() {
        this.setCurrentSelectedCorner(PositionUtils.Corner.NONE);
    }

    public void setCurrentSelectedCorner(PositionUtils.Corner corner) {
        Box box = this.getSelectedSubRegionBox();

        if (box != null) {
            box.setSelectedCorner(corner);
        }
    }

    /**
     * Adds the given SelectionBox, if either replace is true, or there isn't yet a box by the same name.
     *
     * @param box
     * @param replace
     * @return true if the box was successfully added, false if replace was false and there was already a box with the same name
     */
    public boolean addSubRegionBox(Box box, boolean replace) {
        if (replace || this.subRegionBoxes.containsKey(box.getName()) == false) {
            this.subRegionBoxes.put(box.getName(), box);
            this.markDirty();
            return true;
        }

        return false;
    }

    public void removeAllSubRegionBoxes() {
        this.subRegionBoxes.clear();
        this.markDirty();
    }

    public boolean removeSubRegionBox(String name) {
        boolean success = this.subRegionBoxes.remove(name) != null;
        this.markDirty();

        if (success && name.equals(this.currentBox)) {
            this.currentBox = null;
        }

        return success;
    }

    public void moveEntireSelectionTo(BlockPos newOrigin, boolean printMessage) {
        BlockPos old = this.getEffectiveOrigin();
        BlockPos diff = newOrigin.subtract(old);

        for (Box box : this.subRegionBoxes.values()) {
            if (box.getPos1() != null) {
                this.setSubRegionCornerPos(box, PositionUtils.Corner.CORNER_1, box.getPos1().offset(diff));
            }

            if (box.getPos2() != null) {
                this.setSubRegionCornerPos(box, PositionUtils.Corner.CORNER_2, box.getPos2().offset(diff));
            }
        }

        if (this.getExplicitOrigin() != null) {
            this.setExplicitOrigin(newOrigin);
        }

        if (printMessage) {
            String oldStr = String.format("x: %d, y: %d, z: %d", old.getX(), old.getY(), old.getZ());
            String newStr = String.format("x: %d, y: %d, z: %d", newOrigin.getX(), newOrigin.getY(), newOrigin.getZ());
        }
    }

    public void moveSelectedElement(Direction direction, int amount) {
        Box box = this.getSelectedSubRegionBox();

        if (this.isOriginSelected()) {
            if (this.getExplicitOrigin() != null) {
                this.setExplicitOrigin(this.getExplicitOrigin().relative(direction, amount));
            }
        } else if (box != null) {
            PositionUtils.Corner corner = box.getSelectedCorner();

            if ((corner == PositionUtils.Corner.NONE || corner == PositionUtils.Corner.CORNER_1) && box.getPos1() != null) {
                BlockPos pos = this.getSubRegionCornerPos(box, PositionUtils.Corner.CORNER_1).relative(direction, amount);
                this.setSubRegionCornerPos(box, PositionUtils.Corner.CORNER_1, pos);
            }

            if ((corner == PositionUtils.Corner.NONE || corner == PositionUtils.Corner.CORNER_2) && box.getPos2() != null) {
                BlockPos pos = this.getSubRegionCornerPos(box, PositionUtils.Corner.CORNER_2).relative(direction, amount);
                this.setSubRegionCornerPos(box, PositionUtils.Corner.CORNER_2, pos);
            }
        }
    }

    public void setSelectedSubRegionCornerPos(BlockPos pos, PositionUtils.Corner corner) {
        Box box = this.getSelectedSubRegionBox();

        if (box != null) {
            this.setSubRegionCornerPos(box, corner, pos);
        }
    }

    public void setSubRegionCornerPos(Box box, PositionUtils.Corner corner, BlockPos pos) {
        if (corner == PositionUtils.Corner.CORNER_1) {
            box.setPos1(pos);
            this.markDirty();
        } else if (corner == PositionUtils.Corner.CORNER_2) {
            box.setPos2(pos);
            this.markDirty();
        }
    }

    public void setCoordinate(@Nullable Box box, PositionUtils.Corner corner, PositionUtils.CoordinateType type, int value) {
        if (box != null && corner != null && corner != PositionUtils.Corner.NONE) {
            box.setCoordinate(value, corner, type);
            this.markDirty();
        } else if (this.explicitOrigin != null) {
            this.setExplicitOrigin(PositionUtils.getModifiedPosition(this.explicitOrigin, value, type));
        }
    }

    public BlockPos getSubRegionCornerPos(Box box, PositionUtils.Corner corner) {
        return corner == PositionUtils.Corner.CORNER_2 ? box.getPos2() : box.getPos1();
    }

    public AreaSelection copy() {
        return fromJson(this.toJson());
    }

    public static AreaSelection fromJson(JsonObject obj) {
        AreaSelection area = new AreaSelection();

        if (JsonUtils.hasArray(obj, "boxes")) {
            JsonArray arr = obj.get("boxes").getAsJsonArray();
            final int size = arr.size();

            for (int i = 0; i < size; i++) {
                JsonElement el = arr.get(i);

                if (el.isJsonObject()) {
                    Box box = Box.fromJson(el.getAsJsonObject());

                    if (box != null) {
                        area.subRegionBoxes.put(box.getName(), box);
                    }
                }
            }
        }

        if (JsonUtils.hasString(obj, "name")) {
            area.name = obj.get("name").getAsString();
        }

        if (JsonUtils.hasString(obj, "current")) {
            area.currentBox = obj.get("current").getAsString();
        }

        BlockPos pos = JsonUtils.blockPosFromJson(obj, "origin");

        if (pos != null) {
            area.setExplicitOrigin(pos);
        } else {
            area.updateCalculatedOrigin();
        }

        return area;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        JsonArray arr = new JsonArray();

        for (Box box : this.subRegionBoxes.values()) {
            JsonObject o = box.toJson();

            if (o != null) {
                arr.add(o);
            }
        }

        obj.add("name", new JsonPrimitive(this.name));

        if (arr.size() > 0) {
            if (this.currentBox != null) {
                obj.add("current", new JsonPrimitive(this.currentBox));
            }

            obj.add("boxes", arr);
        }

        if (this.getExplicitOrigin() != null) {
            obj.add("origin", JsonUtils.blockPosToJson(this.getExplicitOrigin()));
        }

        return obj;
    }
}
