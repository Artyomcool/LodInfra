package com.github.artyomcool.lodinfra.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXChip;
import com.jfoenix.controls.JFXChipView;
import com.jfoenix.svg.SVGGlyph;
import com.sun.javafx.scene.control.behavior.BehaviorBase;
import com.sun.javafx.scene.control.behavior.FocusTraversalInputMap;
import com.sun.javafx.scene.control.inputmap.InputMap;
import com.sun.javafx.scene.traversal.Direction;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.util.StringConverter;

import java.util.List;

public class JFXChipViewSkin<T> extends SkinBase<JFXChipView<T>> {

    private static final PseudoClass PSEUDO_CLASS_ERROR = PseudoClass.getPseudoClass("error");

    private CustomFlowPane root;
    private JFXChipView<T> control;
    private FakeFocusTextArea editor;
    private com.jfoenix.skins.JFXChipViewSkin.ChipsAutoComplete<T> autoCompletePopup;

    private boolean moveToNewLine = false;
    private boolean editorOnNewLine = true;
    private double availableWidth;
    private double requiredWidth;

    private final ChipViewBehaviorBase<T> behavior;
    @SuppressWarnings("FieldCanBeLocal")
    private final ListChangeListener<T> chipsChangeListeners = change -> {
        while (change.next()) {
            for (T item : change.getRemoved()) {
                for (int i = root.getChildren().size() - 2; i >= 0; i--) {
                    Node child = root.getChildren().get(i);
                    if (child instanceof JFXChip) {
                        if (((JFXChip<?>) child).getItem() == item) {
                            root.getChildren().remove(i);
                            break;
                        }
                    }
                }
            }
            for (T item : change.getAddedSubList()) {
                createChip(item);
            }
        }
    };

    public JFXChipViewSkin(JFXChipView<T> control) {
        super(control);
        this.control = control;
        this.behavior = new JFXChipViewSkin.ChipViewBehaviorBase<>(control);
        root = new CustomFlowPane();
        root.setOnMouseClicked(event -> editor.requestFocus());
        root.getStyleClass().add("chips-pane");
        setupEditor();

        getChildren().add(root);

        // init auto complete
        autoCompletePopup = (com.jfoenix.skins.JFXChipViewSkin.ChipsAutoComplete<T>) getSkinnable().getAutoCompletePopup();
        autoCompletePopup.setSelectionHandler(event -> {
            T selectedItem = event.getObject();
            if (getSkinnable().getSelectionHandler() != null) {
                selectedItem = getSkinnable().getSelectionHandler().apply(selectedItem);
            }
            getSkinnable().getChips().add(selectedItem);
            editor.clear();
        });

        // create initial chips
        for (T item : control.getChips()) {
            createChip(item);
        }
        control.getChips().addListener(new WeakListChangeListener<>(chipsChangeListeners));

    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return root.minHeight(width - rightInset - leftInset) + topInset + bottomInset;
    }

    private void setupEditor() {
        editor = new FakeFocusTextArea();
        editor.setManaged(false);
        editor.getStyleClass().add("editor");
        editor.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() != KeyCode.ENTER) {
                getSkinnable().pseudoClassStateChanged(PSEUDO_CLASS_ERROR, false);
            }
        });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case ENTER:
                    if (!editor.getText().trim().isEmpty()) {
                        try {
                            final StringConverter<T> sc = control.getConverter();
                            final T item = sc.fromString(editor.getText());
                            if (item != null) {
                                getSkinnable().getChips().add(item);
                            }
                            editor.clear();
                            autoCompletePopup.hide();
                        } catch (Exception ex) {
                            getSkinnable().pseudoClassStateChanged(PSEUDO_CLASS_ERROR, true);
                        }
                    }
                    event.consume();
                    break;

                case BACK_SPACE:
                    ObservableList<T> chips = getSkinnable().getChips();
                    int size = chips.size();
                    if ((size > 0) && editor.getText().isEmpty()) {
                        chips.remove(size - 1);
                        if (autoCompletePopup.isShowing()) {
                            autoCompletePopup.hide();
                        }
                    }
                    break;

                case SPACE:
                    if (event.isControlDown()) {
                        if (!autoCompletePopup.getFilteredSuggestions().isEmpty()) {
                            autoCompletePopup.show(editor);
                        }
                    }
                    break;
            }
        });

        editor.textProperty().addListener(observable -> {
            // update editor position
            // 13 is the default scroll bar width
            requiredWidth = editor.snappedLeftInset() + computeTextContentWidth(editor) + editor.snappedRightInset() ;
            if (availableWidth < requiredWidth && !editorOnNewLine && !moveToNewLine) {
                moveToNewLine = true;
                root.requestLayout();
            } else if (availableWidth > requiredWidth && editorOnNewLine && moveToNewLine) {
                moveToNewLine = false;
                root.requestLayout();
            }
            // show popup
            autoCompletePopup.filter(item -> getSkinnable().getPredicate().test(item, editor.getText()));
            if (autoCompletePopup.getFilteredSuggestions().isEmpty()) {
                autoCompletePopup.hide();
            } else {
                autoCompletePopup.show(editor);
            }
        });

        editor.promptTextProperty().bind(control.promptTextProperty());
        root.getChildren().add(editor);

        // add control listeners
        control.focusedProperty().addListener((obj, oldVal, newVal) -> {
            if (editor != null) {
                editor.setFakeFocus(newVal);
            }
        });
        control.addEventFilter(KeyEvent.ANY, ke -> {
            if (editor != null) {
                if (ke.getTarget().equals(editor)) {
                    return;
                }
                // only pass event
                if (ke.getTarget().equals(control)) {
                    switch (ke.getCode()) {
                        case ESCAPE:
                        case F10:
                            // Allow to bubble up.
                            break;
                        default:
                            editor.fireEvent(ke.copyFor(editor, editor));
                            ke.consume();
                    }
                }
            }
        });
    }

    // these methods are called inside the chips items change listener
    private void createChip(T item) {
        JFXChip<T> chip = new JFXChip<>(getSkinnable(), item) {
            {
                JFXButton closeButton = new JFXButton(null, new SVGGlyph());
                closeButton.getStyleClass().add("close-button");
                closeButton.setOnAction((event) -> view.getChips().remove(item));

                String tagString;
                if (getItem() instanceof String) {
                    tagString = (String) getItem();
                } else {
                    tagString = view.getConverter().toString(getItem());
                }
                Label label = new Label(tagString);
                getChildren().setAll(new HBox(label, closeButton));
            }
        };
        int size = root.getChildren().size();
        root.getChildren().add(size - 1, chip);
    }

    private double computeTextContentWidth(TextInputControl editor) {
        Text text = new Text(editor.getText());
        text.setFont(editor.getFont());
        text.applyCss();
        return text.getLayoutBounds().getWidth();
    }


    private class CustomFlowPane extends FlowPane {
        double initOffset = 8;

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            updateEditorPosition();
        }

        @Override
        protected double computePrefHeight(double forWidth) {
            editor.setManaged(true);
            double height = super.computePrefHeight(forWidth);
            editor.setManaged(false);
            return height;
        }

        private VPos getRowVAlignmentInternal() {
            VPos localPos = getRowValignment();
            return localPos == null ? VPos.CENTER : localPos;
        }

        private HPos getColumnHAlignmentInternal() {
            HPos localPos = getColumnHalignment();
            return localPos == null ? HPos.LEFT : localPos;
        }

        public void updateEditorPosition() {
            final Insets insets = getInsets();
            final double width = getWidth();
            final double top = insets.getTop();
            final double left = insets.getLeft();
            final double right = insets.getRight();
            final double insideWidth = width - left - right;
            final double newLineEditorX = right + initOffset;

            final List<Node> managedChildren = getManagedChildren();
            final int mangedChildrenSize = managedChildren.size();
            if (mangedChildrenSize > 0) {
                Region lastChild = (Region) managedChildren.get(mangedChildrenSize - 1);
                double contentHeight = lastChild.getHeight() + lastChild.getLayoutY();
                availableWidth = insideWidth - lastChild.getBoundsInParent().getMaxX();
                double minWidth = editor.getMinWidth();
                minWidth = minWidth < 0 ? 100 : minWidth;
                minWidth = Math.max(minWidth, requiredWidth);

                if (availableWidth > requiredWidth) {
                    moveToNewLine = false;
                }

                if (availableWidth < minWidth || moveToNewLine) {
                    layoutInArea(editor,
                            newLineEditorX,
                            contentHeight + root.getVgap(),
                            insideWidth - initOffset,
                            editor.prefHeight(-1),
                            0, getColumnHAlignmentInternal(), VPos.TOP);
                    editorOnNewLine = true;
                } else {
                    layoutInArea(editor,
                            lastChild.getBoundsInParent().getMaxX() + root.getHgap(),
                            lastChild.getLayoutY(),
                            availableWidth - root.getHgap(),
                            lastChild.getHeight(),
                            0, getColumnHAlignmentInternal(), getRowVAlignmentInternal());
                    editorOnNewLine = false;
                }
            } else {
                layoutInArea(editor,
                        newLineEditorX,
                        top,
                        insideWidth - initOffset,
                        editor.prefHeight(-1)
                        , 0, getColumnHAlignmentInternal(), VPos.TOP);
                editorOnNewLine = true;
            }
        }

    }

    final class FakeFocusTextArea extends TextField {
        @Override
        public void requestFocus() {
            if (getSkinnable() != null) {
                getSkinnable().requestFocus();
            }
        }

        public void setFakeFocus(boolean b) {
            setFocused(b);
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            switch (attribute) {
                case FOCUS_ITEM:
                    // keep focus on parent control
                    return getSkinnable();
                default:
                    return super.queryAccessibleAttribute(attribute, parameters);
            }
        }
    }

    public void dispose() {
        super.dispose();
        if (this.behavior != null) {
            this.behavior.dispose();
        }
    }

    final static class ChipViewBehaviorBase<T> extends BehaviorBase<JFXChipView<T>> {
        public ChipViewBehaviorBase(JFXChipView<T> control) {
            super(control);
        }

        @Override
        public InputMap<JFXChipView<T>> getInputMap() {
            return new InputMap<>(getNode());
        }

        public void traverse(Node node, Direction dir) {
            FocusTraversalInputMap.traverse(node, dir);
        }
    }
}
