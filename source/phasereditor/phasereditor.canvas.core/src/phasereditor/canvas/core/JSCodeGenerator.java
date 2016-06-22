// The MIT License (MIT)
//
// Copyright (c) 2015, 2016 Arian Fornaris
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the
// following conditions: The above copyright notice and this permission
// notice shall be included in all copies or substantial portions of the
// Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
// NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.
package phasereditor.canvas.core;

import static java.lang.String.format;

import phasereditor.assetpack.core.IAssetFrameModel;
import phasereditor.assetpack.core.IAssetKey;
import phasereditor.assetpack.core.ImageAssetModel;
import phasereditor.assetpack.core.SpritesheetAssetModel;
import phasereditor.lic.LicCore;

/**
 * @author arian
 *
 */
public class JSCodeGenerator implements ICodeGenerator {

	/**
	 * 
	 */
	private static final String YOU_CAN_INSERT_CODE_HERE = "// you can insert code here";
	public static final String PRE_INIT_CODE_BEGIN = "/* --- pre-init-begin --- */";
	public static final String PRE_INIT_CODE_END = "/* --- pre-init-end --- */";
	public static final String POST_INIT_CODE_BEGIN = "/* --- post-init-begin --- */";
	public static final String POST_INIT_CODE_END = "/* --- post-init-end --- */";
	public static final String END_GENERATED_CODE = "/* --- end generated code --- */";

	@Override
	public String generate(WorldModel model, String replace) {
		String tabs1 = tabs(1);

		String preInit = "\n\n" + tabs1 + YOU_CAN_INSERT_CODE_HERE + "\n\n" + tabs1;
		String postInit = "\n\n" + tabs1 + YOU_CAN_INSERT_CODE_HERE + "\n\n" + tabs1;
		String postGen = "\n\n" + YOU_CAN_INSERT_CODE_HERE + "\n\n";

		if (replace != null) {
			int i;
			int j;
			i = replace.indexOf(PRE_INIT_CODE_BEGIN);
			j = replace.indexOf(PRE_INIT_CODE_END);
			if (i != -1 && j != -1) {
				preInit = replace.substring(i + PRE_INIT_CODE_BEGIN.length(), j);
			}

			i = replace.indexOf(POST_INIT_CODE_BEGIN);
			j = replace.indexOf(POST_INIT_CODE_END);
			if (i != -1 && j != -1) {
				postInit = replace.substring(i + POST_INIT_CODE_BEGIN.length(), j);
			}

			i = replace.indexOf(END_GENERATED_CODE);
			if (i != -1) {
				postGen = replace.substring(i + END_GENERATED_CODE.length());
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("// Generated by " + LicCore.PRODUCT_NAME + "\n\n");
		String classname = model.getClassName();
		sb.append("/**\n");
		sb.append(" * " + classname + ".\n");
		sb.append(" * @param {Phaser.Game} aGame The game.\n");
		sb.append(" * @param {Phaser.Group} aParent The parent group. If not given the game world will be used instead.\n");
		sb.append(" */\n");
		sb.append("function " + classname + "(aGame, aParent) {\n");
		sb.append(tabs1 + "Phaser.Group.call(this, aGame, aParent);\n\n");

		sb.append(tabs1 + PRE_INIT_CODE_BEGIN);
		sb.append(preInit);
		sb.append(PRE_INIT_CODE_END + "\n");
		sb.append("\n");

		{
			int i = 0;
			int last = model.getChildren().size() - 1;
			for (BaseObjectModel child : model.getChildren()) {
				generate(1, sb, child);
				if (i < last) {
					sb.append("\n");
				}
				i++;
			}
		}

		sb.append("\n");

		// public fields
		StringBuilder pubs = new StringBuilder();
		model.walk(obj -> {
			if (!(obj instanceof WorldModel) && obj.isEditorPublic() && obj.isEditorGenerate()) {
				String name = obj.getEditorName();
				String camel = "f" + name.substring(0, 1).toUpperCase() + name.substring(1);
				pubs.append(tabs1 + "this." + camel + " = " + name + ";\n");
			}
		});

		if (pubs.length() > 0) {
			sb.append(tabs1 + " // public fields\n\n");
			sb.append(pubs);
		}

		sb.append("\n");

		sb.append(tabs1 + POST_INIT_CODE_BEGIN);
		sb.append(postInit);
		sb.append(POST_INIT_CODE_END + "\n");

		sb.append("}\n\n");

		sb.append("/** @type Phaser.Group */\n");
		sb.append("var " + classname + "_proto = Object.create(Phaser.Group.prototype);\n");
		sb.append(classname + ".prototype = " + classname + "_proto;\n");
		sb.append(classname + ".prototype.constructor = Phaser.Group;\n");
		sb.append("\n");

		sb.append(END_GENERATED_CODE);
		sb.append(postGen);

		return sb.toString();
	}

	private static void generate(int indent, StringBuilder sb, BaseObjectModel model) {
		if (!model.isEditorGenerate()) {
			return;
		}

		if (model instanceof GroupModel) {
			generateGroup(indent, sb, (GroupModel) model);
		} else if (model instanceof BaseSpriteModel) {
			generateSprite(indent, sb, (BaseSpriteModel) model);
		}
	}

	private static void generateSprite(int indent, StringBuilder sb, BaseSpriteModel model) {

		// properties

		StringBuilder sbProps = new StringBuilder();

		generateDisplayProps(indent, sbProps, model);

		generateSpriteProps(indent, sbProps, model);

		if (model instanceof TileSpriteModel) {
			generateTileProps(indent, sbProps, (TileSpriteModel) model);
		}

		// create method

		sb.append(tabs(indent));
		String parVar = model.getParent().isWorldModel() ? "this" : model.getParent().getEditorName();
		if (sbProps.length() > 0 || model.isEditorPublic()) {
			sb.append("var " + model.getEditorName() + " = ");
		}
		sb.append("this.game.add.");

		if (model instanceof ImageSpriteModel) {
			ImageSpriteModel image = (ImageSpriteModel) model;
			sb.append("sprite(" + // sprite
					round(image.getX())// x
					+ ", " + round(image.getY()) // y
					+ ", '" + image.getAssetKey().getKey() + "'" // key
					+ ", null" // frame
					+ ", " + parVar // group
					+ ")");
		} else if (model instanceof SpritesheetSpriteModel || model instanceof AtlasSpriteModel) {
			AssetSpriteModel<?> sprite = (AssetSpriteModel<?>) model;
			IAssetKey frame = sprite.getAssetKey();
			String frameValue = frame instanceof SpritesheetAssetModel.FrameModel
					? Integer.toString(((SpritesheetAssetModel.FrameModel) frame).getIndex())
					: "'" + frame.getKey() + "'";
			sb.append("sprite(" + // sprite
					round(sprite.getX())// x
					+ ", " + round(sprite.getY()) // y
					+ ", '" + sprite.getAssetKey().getAsset().getKey() + "'" // key
					+ ", " + frameValue // frame
					+ ", " + parVar // group
					+ ")");
		} else if (model instanceof ButtonSpriteModel) {
			ButtonSpriteModel button = (ButtonSpriteModel) model;
			String outFrameKey;
			if (button.getAssetKey().getAsset() instanceof ImageAssetModel) {
				// buttons based on image do not have outFrames
				outFrameKey = "null";
			} else {
				outFrameKey = frameKey((IAssetFrameModel) button.getAssetKey());
			}

			sb.append("button(" + // sprite
					round(button.getX())// x
					+ ", " + round(button.getY()) // y
					+ ", '" + button.getAssetKey().getAsset().getKey() + "'" // key
					+ ", " + emptyStringToNull(button.getCallback()) // callback
					+ ", " + emptyStringToNull(button.getCallbackContext()) // context
					+ ", " + frameKey(button.getOverFrame())// overFrame
					+ ", " + outFrameKey// outFrame
					+ ", " + frameKey(button.getDownFrame())// downFrame
					+ ", " + frameKey(button.getUpFrame())// upFrame
					+ ", " + parVar // group
					+ ")");
		} else if (model instanceof TileSpriteModel) {
			TileSpriteModel tile = (TileSpriteModel) model;
			boolean isImage = tile.getAssetKey().getAsset() instanceof ImageAssetModel;
			sb.append("tileSprite(" + // sprite
					round(tile.getX())// x
					+ ", " + round(tile.getY()) // y
					+ ", " + round(tile.getWidth()) // width
					+ ", " + round(tile.getHeight()) // height
					+ ", '" + tile.getAssetKey().getAsset().getKey() + "'" // key
					+ ", " + (isImage ? "null" : "'" + tile.getAssetKey().getKey() + "'")// frame
					+ ", " + parVar // group
					+ ")");
		}
		sb.append(";\n");

		sb.append(sbProps);
	}

	private static void generateDisplayProps(int indent, StringBuilder sb, BaseObjectModel model) {
		String tabs = tabs(indent);
		String varname = model.getEditorName();

		if (model instanceof GroupModel) {
			if (model.getX() != 0 || model.getY() != 0) {
				sb.append(tabs + varname + ".position.setTo(" + round(model.getX()) + ", " + round(model.getY())
						+ ");\n");
			}
		}

		if (model.getAngle() != 0) {
			sb.append(tabs + varname + ".angle = " + model.getAngle() + ";\n");
		}

		if (model.getScaleX() != 1 || model.getScaleY() != 1) {
			sb.append(tabs + varname + ".scale.setTo(" + model.getScaleX() + ", " + model.getScaleY() + ");\n");
		}

		if (model.getPivotX() != 0 || model.getPivotY() != 0) {
			sb.append(tabs + varname + ".pivot.setTo(" + model.getPivotX() + ", " + model.getPivotY() + ");\n");
		}
	}

	private static void generateSpriteProps(int indent, StringBuilder sb, BaseSpriteModel model) {
		String tabs = tabs(indent);
		String varname = model.getEditorName();

		if (model.getAnchorX() != 0 || model.getAnchorY() != 0) {
			sb.append(tabs + varname + ".anchor.setTo(" + model.getAnchorX() + ", " + model.getAnchorY() + ");\n");
		}

		if (model.getTint() != null && !model.getTint().equals("0xffffff")) {
			sb.append(tabs + varname + ".tint = " + model.getTint() + ";\n");
		}

		if (!model.getAnimations().isEmpty()) {
			for (AnimationModel anim : model.getAnimations()) {
				sb.append(tabs + varname + ".animations.add(");
				sb.append("'" + anim.getName() + "', [");
				int i = 0;
				for (IAssetFrameModel frame : anim.getFrames()) {
					if (i++ > 0) {
						sb.append(", ");
					}
					if (frame instanceof SpritesheetAssetModel.FrameModel) {
						sb.append(frame.getKey());
					} else {
						sb.append("'" + frame.getKey() + "'");
					}
				}
				sb.append("], " + anim.getFrameRate() + ", " + anim.isLoop() + ");\n");
			}
		}
	}

	private static void generateTileProps(int indent, StringBuilder sb, TileSpriteModel model) {
		String tabs = tabs(indent);
		String varname = model.getEditorName();

		if (model.getTilePositionX() != 0 || model.getTilePositionY() != 0) {
			sb.append(tabs + varname + ".tilePosition.setTo(" + round(model.getTilePositionX()) + ", "
					+ round(model.getTilePositionY()) + ");\n");
		}

		if (model.getTileScaleX() != 1 || model.getTileScaleY() != 1) {
			sb.append(tabs + varname + ".tileScale.setTo(" + model.getTileScaleX() + ", " + model.getTileScaleY()
					+ ");\n");
		}

	}

	private static void generateGroup(int indent, StringBuilder sb, GroupModel group) {
		String tabs = tabs(indent);

		{
			sb.append(tabs);
			sb.append("var " + group.getEditorName() + " = ");
			if (group.isPhysicsGroup()) {
				sb.append("this.game.add.physicsGroup(Phaser.Physics.ARCADE, "
						+ (group.getParent().isWorldModel() ? "this" : group.getParent().getEditorName()) + ");\n");
			} else {
				sb.append(format("this.game.add.group(%s);\n",
						group.getParent().isWorldModel() ? "this" : group.getParent().getEditorName()));

			}
		}

		generateDisplayProps(indent, sb, group);

		if (!group.getChildren().isEmpty()) {
			sb.append("\n");
			int i = 0;
			int last = group.getChildren().size() - 1;

			for (BaseObjectModel child : group.getChildren()) {
				generate(indent, sb, child);
				if (i < last) {
					sb.append("\n");
				}
				i++;
			}
		}
	}

	private static String tabs(int indent) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < indent; i++) {
			sb.append("\t");
		}
		return sb.toString();
	}

	private static String frameKey(IAssetFrameModel frame) {
		if (frame == null) {
			return "null";
		}

		if (frame instanceof SpritesheetAssetModel.FrameModel) {
			return Integer.toString(((SpritesheetAssetModel.FrameModel) frame).getIndex());
		}

		return "'" + frame.getKey() + "'";
	}

	private static String emptyStringToNull(String str) {
		return str == null ? null : (str.trim().length() == 0 ? null : str);
	}

	private static String round(double x) {
		return Integer.toString((int) Math.round(x));
	}
}

