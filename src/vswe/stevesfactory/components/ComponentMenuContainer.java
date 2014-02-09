package vswe.stevesfactory.components;


import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import vswe.stevesfactory.CollisionHelper;
import vswe.stevesfactory.Localization;
import vswe.stevesfactory.blocks.ConnectionBlock;
import vswe.stevesfactory.blocks.ConnectionBlockType;
import vswe.stevesfactory.blocks.TileEntityManager;
import vswe.stevesfactory.interfaces.Color;
import vswe.stevesfactory.interfaces.ContainerManager;
import vswe.stevesfactory.interfaces.GuiManager;
import vswe.stevesfactory.network.DataBitHelper;
import vswe.stevesfactory.network.DataReader;
import vswe.stevesfactory.network.DataWriter;
import vswe.stevesfactory.network.PacketHandler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public abstract class ComponentMenuContainer extends ComponentMenu {


    private static final int BACK_SRC_X = 46;
    private static final int BACK_SRC_Y = 52;
    private static final int BACK_SIZE_W = 9;
    private static final int BACK_SIZE_H = 9;
    private static final int BACK_X = 108;
    private static final int BACK_Y = 57;

    private static final int INVENTORY_SIZE = 16;
    private static final int INVENTORY_SRC_X = 30;
    private static final int INVENTORY_SRC_Y = 20;

    private static final int RADIO_BUTTON_MULTI_X = 2;
    private static final int RADIO_BUTTON_MULTI_Y = 27;
    private static final int RADIO_BUTTON_SPACING = 15;

    private static final int MENU_WIDTH = 120;
    private static final int TEXT_MULTI_MARGIN_X = 5;
    private static final int TEXT_MULTI_Y = 10;
    private static final int TEXT_MULTI_ERROR_Y = 30;

    private static final int FILTER_BUTTON_X = 90;
    private static final int FILTER_BUTTON_Y = 0;
    private static final int CHECK_BOX_FILTER_INVERT_Y = 55;
    private static final int FILTER_RESET_BUTTON_X = 70;

    private static final int CHECK_BOX_FILTER_Y = 5;
    private static final int CHECK_BOX_FILTER_SPACING = 12;

    private Page currentPage;
    protected List<Integer> selectedInventories;
    private List<IContainerSelection> inventories;
    protected RadioButtonList radioButtonsMulti;
    private ScrollController<IContainerSelection> scrollController;
    private ConnectionBlockType validType;
    private GuiManager cachedInterface;
    private List<Button> buttons;
    private static final ContainerFilter filter = new ContainerFilter(); //this one is static so all of the menus will share the selection
    private List<Variable> filterVariables;



    protected EnumSet<ConnectionBlockType> getValidTypes() {
        return EnumSet.of(validType);
    }

    public ComponentMenuContainer(FlowComponent parent, ConnectionBlockType validType) {
        super(parent);
        this.validType = validType;

        selectedInventories = new ArrayList<Integer>();
        filterVariables = new ArrayList<Variable>();
        radioButtonsMulti = new RadioButtonList() {
            @Override
            public void updateSelectedOption(int selectedOption) {
               DataWriter dw = getWriterForServerComponentPacket();
               writeRadioButtonData(dw, selectedOption);
               PacketHandler.sendDataToServer(dw);
            }
        };

        initRadioButtons();

        scrollController = new ScrollController<IContainerSelection>(true) {
            @Override
            protected List<IContainerSelection> updateSearch(String search, boolean all) {
                if (search.equals("") || cachedInterface == null) {
                    return new ArrayList<IContainerSelection>();
                }

                if (search.equals(".var")) {
                    return new ArrayList<IContainerSelection>(filterVariables);
                }

                boolean noFilter = search.equals(".nofilter");

                List<IContainerSelection> ret = new ArrayList<IContainerSelection>(inventories);

                Iterator<IContainerSelection> iterator = ret.iterator();
                while (iterator.hasNext()) {
                    IContainerSelection element = iterator.next();

                    if(!element.isVariable()) {
                        ConnectionBlock block = (ConnectionBlock)element;
                        if (noFilter) {
                            continue;
                        }else if (all || search.contains(block.getName(cachedInterface).toLowerCase())) {
                            if (filter.matches(getParent().getManager(), selectedInventories, block)) {
                                continue;
                            }
                        }
                    }

                    iterator.remove();
                }


                return ret;
            }

            @Override
            protected void onClick(IContainerSelection iContainerSelection, int button) {
                setSelectedInventoryAndSync(iContainerSelection.getId(), !selectedInventories.contains(iContainerSelection.getId()));
            }

            @Override
            protected void draw(GuiManager gui, IContainerSelection iContainerSelection, int x, int y, boolean hover) {
                drawContainer(gui, iContainerSelection, selectedInventories, x, y, hover);
            }

            @Override
            protected List<String> getMouseOver(IContainerSelection iContainerSelection) {
                return getMouseOverForContainer(iContainerSelection, selectedInventories);
            }
        };

        buttons = new ArrayList<Button>();
        buttons.add(new PageButton(Localization.FILTER_SHORT, Page.MAIN, Localization.FILTER_LONG, Page.FILTER, false, 102, 21));
        buttons.add(new PageButton(Localization.MULTI_SHORT, Page.MAIN, Localization.MULTI_LONG, Page.MULTI, false, 111, 21));

        ComponentMenuContainer.Page[] subFilterPages = {ComponentMenuContainer.Page.POSITION, ComponentMenuContainer.Page.DISTANCE, ComponentMenuContainer.Page.SELECTION, ComponentMenuContainer.Page.VARIABLE};

        for (int i = 0; i < subFilterPages.length; i++) {
           buttons.add(new ComponentMenuContainer.PageButton(Localization.SUB_MENU_SHORT, ComponentMenuContainer.Page.FILTER, Localization.SUB_MENU_LONG, subFilterPages[i], true, FILTER_BUTTON_X, CHECK_BOX_FILTER_Y + CHECK_BOX_FILTER_SPACING * i + FILTER_BUTTON_Y));
        }
        buttons.add(new ComponentMenuContainer.Button(Localization.CLEAR_SHORT, ComponentMenuContainer.Page.FILTER, Localization.CLEAR_LONG, true, FILTER_RESET_BUTTON_X, CHECK_BOX_FILTER_INVERT_Y) {
            @Override
            void onClick() {
                filter.clear();
            }
        });

        buttons.add(new Button(Localization.SELECT_ALL_SHORT, Page.MAIN, Localization.SELECT_ALL_LONG, false, 102, 51) {
            @Override
            void onClick() {
                for (IContainerSelection iContainerSelection : scrollController.getResult()) {
                    if (!selectedInventories.contains(iContainerSelection.getId())) {
                        scrollController.onClick(iContainerSelection, 0);
                    }
                }
            }
        });

        buttons.add(new Button(Localization.SELECT_NONE_SHORT, Page.MAIN, Localization.SELECT_NONE_LONG, false, 111, 51) {
            @Override
            void onClick() {
                for (IContainerSelection iContainerSelection : scrollController.getResult()) {
                    if (selectedInventories.contains(iContainerSelection.getId())) {
                        scrollController.onClick(iContainerSelection, 0);
                    }
                }
            }
        });

        buttons.add(new Button(Localization.SELECT_INVERT_SHORT, Page.MAIN, Localization.SELECT_INVERT_LONG, false, 106, 60) {
            @Override
            void onClick() {
                for (IContainerSelection iContainerSelection : scrollController.getResult()) {
                    scrollController.onClick(iContainerSelection, 0);
                }
            }
        });

        currentPage = Page.MAIN;
    }
    @SideOnly(Side.CLIENT)
    void drawContainer(GuiManager gui, IContainerSelection iContainerSelection, List<Integer> selected, int x, int y, boolean hover) {
        int srcInventoryX = selected.contains(iContainerSelection.getId()) ? 1 : 0;
        int srcInventoryY = hover ? 1 : 0;

        gui.drawTexture(x, y, INVENTORY_SRC_X + srcInventoryX * INVENTORY_SIZE, INVENTORY_SRC_Y + srcInventoryY * INVENTORY_SIZE, INVENTORY_SIZE, INVENTORY_SIZE);
        iContainerSelection.draw(gui, x, y);
    }

    List<String> getMouseOverForContainer(IContainerSelection iContainerSelection, List<Integer> selected) {
        List<String> ret = new ArrayList<String>();
        if (cachedInterface != null) {
            String[] desc = iContainerSelection.getDescription(cachedInterface).split("\n");
            for (String s : desc) {
                ret.add(s);
            }
            if (selected.contains(iContainerSelection.getId())) {
                ret.add(Color.GREEN + Localization.SELECTED.toString());
            }
        }
        return ret;
    }



    protected void initRadioButtons() {
        radioButtonsMulti.add(new RadioButtonInventory(0, Localization.RUN_SHARED_ONCE));
        radioButtonsMulti.add(new RadioButtonInventory(1, Localization.RUN_ONE_PER_TARGET));
    }

    public Page getCurrentPage() {
        return currentPage;
    }

    public List<Variable> getFilterVariables() {
        return filterVariables;
    }

    protected class RadioButtonInventory extends RadioButton {

        public RadioButtonInventory(int id, Localization text) {
            super(RADIO_BUTTON_MULTI_X, RADIO_BUTTON_MULTI_Y + id * RADIO_BUTTON_SPACING, text);
        }
    }



    @SideOnly(Side.CLIENT)
    @Override
    public void draw(GuiManager gui, int mX, int mY) {
        cachedInterface = gui;
        filter.currentMenu = this;
        if (currentPage == Page.MAIN) {
            inventories = getInventories(gui.getManager());
            scrollController.draw(gui, mX, mY);

        }else if (currentPage == Page.MULTI) {
            gui.drawCenteredString(selectedInventories.size() + " " + Localization.SELECTED_CONTAINERS.toString(), TEXT_MULTI_MARGIN_X, TEXT_MULTI_Y, 0.9F, MENU_WIDTH - TEXT_MULTI_MARGIN_X * 2, 0x404040);
            String error = null;

            if (radioButtonsMulti.size() == 0) {
                error = Localization.NO_MULTI_SETTING.toString();
            }else if(!hasMultipleInventories()) {
                error = Localization.SINGLE_SELECTED.toString();
            }

            if (error != null) {
                gui.drawSplitString(error, TEXT_MULTI_MARGIN_X, TEXT_MULTI_ERROR_Y, MENU_WIDTH - TEXT_MULTI_MARGIN_X * 2, 0.7F, 0x404040);
            }
            if (hasMultipleInventories()) {
                radioButtonsMulti.draw(gui, mX, mY);
            }
        }else if(currentPage == Page.POSITION) {
            gui.drawString(Localization.RELATIVE_COORDINATES.toString(), 5, 60, 0.5F, 0x404040);
        }else if(currentPage == Page.SELECTION) {
            filter.radioButtonsSelection.draw(gui, mX, mY);
        }else if(currentPage == Page.VARIABLE) {
            filter.radioButtonVariable.draw(gui, mX, mY);
            if (filter.isVariableListVisible()) {
                inventories = getInventories(gui.getManager());
                filter.scrollControllerVariable.draw(gui, mX, mY);
            }
        }

        filter.textBoxes.draw(gui, mX, mY);
        for (Button button : buttons) {
            button.draw(gui, mX, mY);
        }
        filter.checkBoxes.draw(gui, mX, mY);

        if (currentPage.parent != null) {
            int srcBackX = inBackBounds(mX, mY) ? 1 : 0;

            gui.drawTexture(BACK_X, BACK_Y, BACK_SRC_X + srcBackX * BACK_SIZE_W, BACK_SRC_Y, BACK_SIZE_W, BACK_SIZE_H);
        }
    }

    private boolean inBackBounds(int mX, int mY) {
        return CollisionHelper.inBounds(BACK_X, BACK_Y, BACK_SIZE_W, BACK_SIZE_H, mX, mY);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void drawMouseOver(GuiManager gui, int mX, int mY) {
        filter.currentMenu = this;
        if (currentPage == Page.MAIN) {
            scrollController.drawMouseOver(gui, mX, mY);
        }else if(currentPage == Page.VARIABLE && filter.isVariableListVisible()) {
            filter.scrollControllerVariable.drawMouseOver(gui, mX, mY);
        }else if(currentPage == Page.POSITION) {
            if (CollisionHelper.inBounds(5, 60, MENU_WIDTH - 20, 5, mX, mY)) {
                String str = Localization.ABSOLUTE_RANGES.toString() + ":";

                str += "\n" + Localization.X.toString() + " (" + (filter.lowerRange[0].getNumber() + getParent().getManager().xCoord) + ", " + (filter.higherRange[0].getNumber() + getParent().getManager().xCoord) + ")";
                str += "\n" + Localization.Y.toString() + " (" + (filter.lowerRange[1].getNumber() + getParent().getManager().yCoord) + ", " + (filter.higherRange[1].getNumber() + getParent().getManager().yCoord) + ")";
                str += "\n" + Localization.Z.toString() + " (" + (filter.lowerRange[2].getNumber() + getParent().getManager().zCoord) + ", " + (filter.higherRange[2].getNumber() + getParent().getManager().zCoord) + ")";

                gui.drawMouseOver(str, mX, mY);
            }
        }

        for (Button button : buttons) {
            button.drawMouseOver(gui, mX, mY);
        }

        if (currentPage.parent != null && inBackBounds(mX, mY)) {
            gui.drawMouseOver(Localization.GO_BACK.toString(), mX, mY);
        }
    }


    @Override
    public void onClick(int mX, int mY, int b) {
        filter.currentMenu = this;
        if (currentPage == Page.MAIN) {
            scrollController.onClick(mX, mY, b);

        }else if(currentPage == Page.MULTI) {
            if (hasMultipleInventories()) {
                radioButtonsMulti.onClick(mX, mY, b);
            }
        }else if(currentPage == Page.SELECTION) {
            filter.radioButtonsSelection.onClick(mX, mY, b);
        }else if(currentPage == Page.VARIABLE) {
            filter.radioButtonVariable.onClick(mX, mY, b);
            if (filter.isVariableListVisible()) {
                filter.scrollControllerVariable.onClick(mX, mY, b);
            }
        }

        for (Button button : buttons) {
            if (button.inBounds(mX, mY)) {
                button.onClick();
                break;
            }
        }
        filter.checkBoxes.onClick(mX, mY);
        filter.textBoxes.onClick(mX, mY, b);
        if (currentPage.parent != null && inBackBounds(mX, mY)) {
            currentPage = currentPage.parent;
        }
    }

    private boolean hasMultipleInventories() {
        return selectedInventories.size() > 1 || (selectedInventories.size() > 0 && selectedInventories.get(0) < VariableColor.values().length);
    }

    @Override
    public void onDrag(int mX, int mY, boolean isMenuOpen) {

    }

    @Override
    public void onRelease(int mX, int mY, boolean isMenuOpen) {
        filter.currentMenu = this;
        scrollController.onRelease(mX, mY); //no need to check we're on the correct menu, this makes sure the holding always stops
        filter.scrollControllerVariable.onRelease(mX, mY);
    }

    @Override
    public void writeData(DataWriter dw) {
        dw.writeData(getOption(), DataBitHelper.MENU_INVENTORY_MULTI_SELECTION_TYPE);
        dw.writeData(selectedInventories.size(), DataBitHelper.MENU_INVENTORY_SELECTION);
        for (int selectedInventory : selectedInventories) {
            dw.writeData(selectedInventory, DataBitHelper.MENU_INVENTORY_SELECTION);

        }
    }

    @Override
    public void readData(DataReader dr) {
        setOption(dr.readData(DataBitHelper.MENU_INVENTORY_MULTI_SELECTION_TYPE));
        selectedInventories.clear();
        int count = dr.readData(DataBitHelper.MENU_INVENTORY_SELECTION);
        for(int i = 0; i < count; i++) {

            selectedInventories.add(dr.readData(DataBitHelper.MENU_INVENTORY_SELECTION));

        }
    }

    @Override
    public void copyFrom(ComponentMenu menu) {
        setOption(((ComponentMenuContainer) menu).getOption());
        selectedInventories.clear();
        for (int selectedInventory : ((ComponentMenuContainer)menu).selectedInventories) {
            selectedInventories.add(selectedInventory);
        }
    }

    @Override
    public void refreshData(ContainerManager container, ComponentMenu newData) {
        ComponentMenuContainer newDataInv = ((ComponentMenuContainer)newData);

        if (newDataInv.getOption() != getOption()) {
            setOption(newDataInv.getOption());

            DataWriter dw = getWriterForClientComponentPacket(container);
            writeRadioButtonData(dw, getOption());
            PacketHandler.sendDataToListeningClients(container, dw);
        }

        int count = newDataInv.selectedInventories.size();
        for (int i = 0; i < count; i++) {
            int id = newDataInv.selectedInventories.get(i);
            if (!selectedInventories.contains(id)) {
                selectedInventories.add(id);
                sendClientData(container, id, true);
            }

        }

        for (int i = selectedInventories.size() - 1; i >= 0; i--) {
            int id = selectedInventories.get(i);
            if (!newDataInv.selectedInventories.contains(id)) {
                selectedInventories.remove(i);
                sendClientData(container, id, false);
            }
        }
    }

    private void sendClientData(ContainerManager container, int id, boolean select) {
        DataWriter dw = getWriterForClientComponentPacket(container);
        writeData(dw, id, select);
        PacketHandler.sendDataToListeningClients(container, dw);
    }

    @Override
    public void readNetworkComponent(DataReader dr) {
        if (dr.readBoolean()) {
            setOption(dr.readData(DataBitHelper.MENU_INVENTORY_MULTI_SELECTION_TYPE));
        }else{
            int id = dr.readData(DataBitHelper.MENU_INVENTORY_SELECTION);
            if (dr.readBoolean()) {
                selectedInventories.add(id);
            }else{
                selectedInventories.remove((Integer)id);
            }
        }
    }

    private void writeRadioButtonData(DataWriter dw, int option) {
        dw.writeBoolean(true);
        dw.writeData(option, DataBitHelper.MENU_INVENTORY_MULTI_SELECTION_TYPE);
    }

    private void setSelectedInventoryAndSync(int val, boolean select) {
        DataWriter dw = getWriterForServerComponentPacket();
        writeData(dw, val, select);
        PacketHandler.sendDataToServer(dw);
    }

    private void writeData(DataWriter dw, int id, boolean select) {
        dw.writeBoolean(false);
        dw.writeData(id, DataBitHelper.MENU_INVENTORY_SELECTION);
        dw.writeBoolean(select);
    }


    public List<Integer> getSelectedInventories() {
        return selectedInventories;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean onKeyStroke(GuiManager gui, char c, int k) {
        filter.currentMenu = this;
        return currentPage == Page.MAIN ? scrollController.onKeyStroke(gui, c, k) : filter.textBoxes.onKeyStroke(gui, c, k);
    }

    private static final String NBT_SELECTION = "InventorySelection";
    private static final String NBT_SELECTION_ID = "InventoryID";
    private static final String NBT_SHARED = "SharedCommand";
    @Override
    public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
        selectedInventories.clear();
        //in earlier version one could only select one inventory
        if (version < 2) {
            selectedInventories.add((int)nbtTagCompound.getShort(NBT_SELECTION));
            setOption(0);
        }else{
            if (!pickup) {
                NBTTagList tagList = nbtTagCompound.getTagList(NBT_SELECTION);

                for (int i = 0; i < tagList.tagCount(); i++) {
                    NBTTagCompound selectionTag = (NBTTagCompound)tagList.tagAt(i);

                    int id = (int)selectionTag.getShort(NBT_SELECTION_ID);

                    //variables now use the 16 first ids
                    if (version < 7) {
                        id += VariableColor.values().length;
                    }
                    selectedInventories.add(id);
                }
            }
            setOption(nbtTagCompound.getByte(NBT_SHARED));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
        NBTTagList tagList = new NBTTagList();

        if (!pickup) {
            for (int i = 0; i < selectedInventories.size(); i++) {
                NBTTagCompound selectionTag = new NBTTagCompound();

                selectionTag.setShort(NBT_SELECTION_ID, (short)(int)selectedInventories.get(i));
                tagList.appendTag(selectionTag);
            }
        }

        nbtTagCompound.setTag(NBT_SELECTION, tagList);
        nbtTagCompound.setByte(NBT_SHARED, (byte) getOption());
    }




    public void setSelectedInventories(List<Integer> selectedInventories) {
        this.selectedInventories = selectedInventories;
    }

    public int getOption() {
        return radioButtonsMulti.getSelectedOption();
    }

    protected void setOption(int val) {
        radioButtonsMulti.setSelectedOption(val);
    }

    private List<IContainerSelection> getInventories(TileEntityManager manager) {
        EnumSet<ConnectionBlockType> validTypes = getValidTypes();
        List<ConnectionBlock> tempInventories = manager.getConnectedInventories();
        List<IContainerSelection> ret = new ArrayList<IContainerSelection>();
        filterVariables.clear();

        for (int i = 0; i < manager.getVariables().length; i++) {
            Variable variable = manager.getVariables()[i];
            if (isVariableAllowed(validTypes, i)) {
                ret.add(variable);
                filterVariables.add(variable);
            }
        }

        for (ConnectionBlock tempInventory : tempInventories) {
            if (tempInventory.isOfAnyType(validTypes)) {
                ret.add(tempInventory);
            }
        }

        scrollController.updateSearch(); //TODO only do this if the inventories have actually updated
        filter.scrollControllerVariable.updateSearch(); //TODO ditto

        return ret;
    }

    public boolean isVariableAllowed(EnumSet<ConnectionBlockType> validTypes, int i) {
        Variable variable = getParent().getManager().getVariables()[i];
        if (variable.isValid()) {
            EnumSet<ConnectionBlockType> variableValidTypes = ((ComponentMenuContainerTypes)variable.getDeclaration().getMenus().get(1)).getValidTypes();
            for (ConnectionBlockType type : validTypes) {
                if (ConnectionBlock.isOfType(variableValidTypes, type)) {
                    return true;
                }
            }
        }
        return false;
    }

    public enum Page {
        MAIN(null),
        MULTI(MAIN),
        FILTER(MAIN),
        POSITION(FILTER),
        DISTANCE(FILTER),
        SELECTION(FILTER),
        VARIABLE(FILTER);

        private Page parent;

        private Page(Page parent) {
            this.parent = parent;
        }
    }


    private abstract class Button {
        int x, y;
        Localization label;
        Localization description;
        Page page;


        private final int width;
        private final int height;
        private final int srcX;
        private final int srcY;


        protected Button(Localization label, Page page, Localization description, boolean wide, int x, int y) {
            this.x = x;
            this.y = y;
            this.page = page;
            this.label = label;
            this.description = description;

            if (wide) {
                width = 20;
                srcX = 58;
            }else{
                width = 8;
                srcX = 50;
            }
            height = 8;
            srcY = 189;
        }

        abstract void onClick();

        boolean inBounds(int mX, int mY) {
            return isVisible() && CollisionHelper.inBounds(x, y, width, height, mX, mY);
        }

        @SideOnly(Side.CLIENT)
        void draw(GuiManager gui, int mX, int mY) {
            if (isVisible()) {
                gui.drawTexture(x, y, srcX, srcY + (inBounds(mX, mY) ? height : 0), width, height);
                gui.drawCenteredString(label.toString(), x + 1, y + 2, 0.7F, width - 2, 0x404040);
            }
        }

        @SideOnly(Side.CLIENT)
        void drawMouseOver(GuiManager gui, int mX, int mY) {
            if (inBounds(mX, mY)) {
                gui.drawMouseOver(description.toString(), mX, mY);
            }
        }

        boolean isVisible() {
            return currentPage == page;
        }
    }

    private class PageButton extends Button {
        private Page targetPage;

        private PageButton(Localization label, Page page, Localization description, Page targetPage, boolean wide, int x, int y) {
            super(label, page, description, wide, x, y);
            this.targetPage = targetPage;
        }

        @Override
        void onClick() {
            currentPage = targetPage;
        }
    }


}
