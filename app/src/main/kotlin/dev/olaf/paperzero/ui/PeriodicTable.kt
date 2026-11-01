package dev.olaf.paperzero.ui

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.olaf.paperzero.R

/** An element: its proton number, symbol, name, and relative atomic mass as the data sheet gives it (null if none). */
class Element(val z: Int, val symbol: String, val name: String, val mass: String?) {
    /** Its row in the table, 1 to 7. */
    val period: Int = PERIOD_ENDS.indexOfFirst { z <= it } + 1

    /** Its group, 1 to 18; null for the lanthanoids and actinoids, which sit in rows of their own. */
    val group: Int? = run {
        val first = if (period == 1) 1 else PERIOD_ENDS[period - 2] + 1
        val k = z - first
        when (period) {
            1 -> if (z == 1) 1 else 18
            2, 3 -> if (k < 2) k + 1 else k + 11
            4, 5 -> k + 1
            else -> when {
                k < 2 -> k + 1
                k < 17 -> null
                // Past the f-block: hafnium (or rutherfordium) is group 4.
                else -> k - 13
            }
        }
    }

    /** A lanthanoid (57 to 71) or an actinoid (89 to 103). */
    val fBlock: Boolean get() = group == null

    val kind: Kind
        get() = when {
            z in 57..71 -> Kind.Lanthanoid
            z in 89..103 -> Kind.Actinoid
            group == 1 && z != 1 -> Kind.Alkali
            group == 2 -> Kind.AlkalineEarth
            group in 3..12 -> Kind.Transition
            group == 17 -> Kind.Halogen
            group == 18 -> Kind.Noble
            else -> Kind.Other
        }

    enum class Kind(val label: Int, val hue: Long) {
        Alkali(R.string.pt_alkali, 0xFFE8674A),
        AlkalineEarth(R.string.pt_alkaline_earth, 0xFFF0A23B),
        Transition(R.string.pt_transition, 0xFF5B8DEF),
        Halogen(R.string.pt_halogen, 0xFF3FB68B),
        Noble(R.string.pt_noble, 0xFF9C7CF0),
        Lanthanoid(R.string.pt_lanthanoid, 0xFFD678B8),
        Actinoid(R.string.pt_actinoid, 0xFFC2849E),
        Other(R.string.pt_other, 0xFF8E9AA8),
    }

    companion object {
        private val PERIOD_ENDS = listOf(2, 10, 18, 36, 54, 86, 118)

        /** All 118, in order. Masses rounded as on the O-Level data sheet; "-" where it gives none. */
        val all: List<Element> = DATA.trim().split(';').map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, row ->
            val (symbol, name, mass) = row.split(' ')
            Element(i + 1, symbol, name, mass.takeIf { it != "-" })
        }
    }
}

private const val DATA = """
H Hydrogen 1;He Helium 4;Li Lithium 7;Be Beryllium 9;B Boron 11;C Carbon 12;N Nitrogen 14;O Oxygen 16;F Fluorine 19;Ne Neon 20;
Na Sodium 23;Mg Magnesium 24;Al Aluminium 27;Si Silicon 28;P Phosphorus 31;S Sulfur 32;Cl Chlorine 35.5;Ar Argon 40;
K Potassium 39;Ca Calcium 40;Sc Scandium 45;Ti Titanium 48;V Vanadium 51;Cr Chromium 52;Mn Manganese 55;Fe Iron 56;Co Cobalt 59;Ni Nickel 59;Cu Copper 64;Zn Zinc 65;Ga Gallium 70;Ge Germanium 73;As Arsenic 75;Se Selenium 79;Br Bromine 80;Kr Krypton 84;
Rb Rubidium 85;Sr Strontium 88;Y Yttrium 89;Zr Zirconium 91;Nb Niobium 93;Mo Molybdenum 96;Tc Technetium -;Ru Ruthenium 101;Rh Rhodium 103;Pd Palladium 106;Ag Silver 108;Cd Cadmium 112;In Indium 115;Sn Tin 119;Sb Antimony 122;Te Tellurium 128;I Iodine 127;Xe Xenon 131;
Cs Caesium 133;Ba Barium 137;La Lanthanum 139;Ce Cerium 140;Pr Praseodymium 141;Nd Neodymium 144;Pm Promethium -;Sm Samarium 150;Eu Europium 152;Gd Gadolinium 157;Tb Terbium 159;Dy Dysprosium 163;Ho Holmium 165;Er Erbium 167;Tm Thulium 169;Yb Ytterbium 173;Lu Lutetium 175;
Hf Hafnium 178;Ta Tantalum 181;W Tungsten 184;Re Rhenium 186;Os Osmium 190;Ir Iridium 192;Pt Platinum 195;Au Gold 197;Hg Mercury 201;Tl Thallium 204;Pb Lead 207;Bi Bismuth 209;Po Polonium -;At Astatine -;Rn Radon -;
Fr Francium -;Ra Radium -;Ac Actinium -;Th Thorium 232;Pa Protactinium 231;U Uranium 238;Np Neptunium -;Pu Plutonium -;Am Americium -;Cm Curium -;Bk Berkelium -;Cf Californium -;Es Einsteinium -;Fm Fermium -;Md Mendelevium -;No Nobelium -;Lr Lawrencium -;
Rf Rutherfordium -;Db Dubnium -;Sg Seaborgium -;Bh Bohrium -;Hs Hassium -;Mt Meitnerium -;Ds Darmstadtium -;Rg Roentgenium -;Cn Copernicium -;Nh Nihonium -;Fl Flerovium -;Mc Moscovium -;Lv Livermorium -;Ts Tennessine -;Og Oganesson -;
"""

/** O-Level's names for the main groups: I to VII, and 0 for the noble gases. */
private fun groupName(g: Int): String? = when (g) {
    1 -> "I"; 2 -> "II"; 13 -> "III"; 14 -> "IV"; 15 -> "V"; 16 -> "VI"; 17 -> "VII"; 18 -> "0"
    else -> null
}

/**
 * The Periodic Table, over everything: every element with its proton number and relative atomic
 * mass, coloured by family, scrolling both ways. Tap one for its name, group and period.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodicTableDialog(onDismiss: () -> Unit) {
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = MaterialTheme.colorScheme
        Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pt_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
                }
                // The one tapped, in words; or how to read a cell.
                val e = picked?.let { Element.all[it - 1] }
                Text(
                    if (e == null) {
                        stringResource(R.string.pt_hint)
                    } else {
                        listOfNotNull(
                            "${e.name} (${e.symbol})",
                            stringResource(R.string.pt_proton, e.z),
                            e.mass?.let { stringResource(R.string.pt_mass, it) },
                            e.group?.let { g -> stringResource(R.string.pt_group, groupName(g)?.let { "$g ($it)" } ?: g.toString()) },
                            stringResource(R.string.pt_period, e.period),
                            stringResource(e.kind.label),
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (e == null) colors.onSurfaceVariant else colors.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).height(44.dp),
                )
                // The table, fitted to the screen to begin with; pinch (or +) to look closer, drag to move.
                ZoomToFit(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        // Group numbers along the top.
                        Row {
                            Spacer(Modifier.width(LABEL))
                            for (g in 1..18) {
                                Text(
                                    groupName(g) ?: g.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(CELL + GAP),
                                )
                            }
                        }
                        for (period in 1..7) {
                            Row(Modifier.padding(top = GAP), verticalAlignment = Alignment.CenterVertically) {
                                Text(period.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.width(LABEL))
                                for (g in 1..18) {
                                    val here = Element.all.firstOrNull { it.period == period && it.group == g }
                                    Box(Modifier.padding(end = GAP)) {
                                        when {
                                            here != null -> Cell(here, picked == here.z) { picked = here.z }
                                            // Where the lanthanoids and actinoids would go: a pointer to their rows.
                                            g == 3 && period >= 6 -> Gap(if (period == 6) "57–71" else "89–103")
                                            else -> Spacer(Modifier.size(CELL, CELL_H))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        for ((range, label) in listOf(57..71 to R.string.pt_lanthanoid, 89..103 to R.string.pt_actinoid)) {
                            Row(Modifier.padding(top = GAP), verticalAlignment = Alignment.CenterVertically) {
                                // The row's name, in the room before it, so the table stays 18 wide.
                                Text(
                                    stringResource(label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    modifier = Modifier.width(LABEL + (CELL + GAP) * 2).padding(end = 6.dp),
                                )
                                for (z in range) {
                                    val el = Element.all[z - 1]
                                    Box(Modifier.padding(end = GAP)) { Cell(el, picked == z) { picked = z } }
                                }
                            }
                        }
                    }
                }
                // The families' colours.
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Element.Kind.entries.forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.extraSmall, color = Color(k.hue), modifier = Modifier.size(10.dp)) {}
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(k.label), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    stringResource(R.string.pt_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

private val CELL: Dp = 50.dp
private val CELL_H: Dp = 58.dp
private val GAP: Dp = 3.dp
private val LABEL: Dp = 16.dp

@Composable
private fun Cell(e: Element, picked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val hue = Color(e.kind.hue)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = hue.copy(alpha = if (dark) 0.28f else 0.18f).compositeOver(colors.surfaceContainer),
        border = if (picked) BorderStroke(2.dp, colors.onSurface) else BorderStroke(1.dp, hue.copy(alpha = 0.55f)),
        modifier = Modifier.size(CELL, CELL_H),
    ) {
        Column(Modifier.padding(horizontal = 3.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(e.z.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
            Text(e.symbol, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 19.sp), color = colors.onSurface, maxLines = 1)
            Text(e.mass ?: "–", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Gap(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.size(CELL, CELL_H),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.onSurfaceVariant)
        }
    }
}

/**
 * [content] at its own size, scaled down to fit this box to begin with. Pinch to zoom in (up to
 * [MAX_ZOOM] times the fitted size) and drag to move about once it's bigger than the box; the
 * buttons do the same with a tap, and the last one fits it back. Taps go through to the content
 * at any zoom.
 */
@Composable
private fun ZoomToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var natural by remember { mutableStateOf(IntSize.Zero) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val fit = if (natural == IntSize.Zero) 1f else minOf(w / natural.width, h / natural.height)
        // Where the content sits for a scale: centred across and at the top when it fits, else
        // kept from showing past its edges.
        fun settle(o: Offset, scale: Float): Offset {
            val cw = natural.width * scale
            val ch = natural.height * scale
            return Offset(
                if (cw <= w) (w - cw) / 2 else o.x.coerceIn(w - cw, 0f),
                if (ch <= h) 0f else o.y.coerceIn(h - ch, 0f),
            )
        }
        fun zoomTo(next: Float, about: Offset) {
            val z = next.coerceIn(1f, MAX_ZOOM)
            offset = settle(about - (about - offset) * (z / zoom), fit * z)
            zoom = z
        }
        LaunchedEffect(natural, w, h) { offset = settle(offset, fit * zoom) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(natural, w, h) {
                    detectTransformGestures { centroid, pan, gesture, _ ->
                        zoomTo(zoom * gesture, centroid)
                        offset = settle(offset + pan, fit * zoom)
                    }
                },
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .onSizeChanged { natural = it }
                    .graphicsLayer {
                        // Worked out here too, from the size just measured, so the first frame
                        // it shows in is already fitted.
                        val scale = (if (natural == IntSize.Zero) 1f else minOf(w / natural.width, h / natural.height)) * zoom
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        // Out of sight until it's measured and fitted, so it doesn't flash huge.
                        alpha = if (natural == IntSize.Zero) 0f else 1f
                    },
            ) { content() }
        }
        // Zoom buttons, for one hand: closer, further, and back to fitting the screen.
        val centre = Offset(w / 2, h / 2)
        Column(
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ZoomButton(Icons.Rounded.Add, stringResource(R.string.pt_zoom_in), enabled = zoom < MAX_ZOOM) { zoomTo(zoom * 1.6f, centre) }
            ZoomButton(Icons.Rounded.Remove, stringResource(R.string.pt_zoom_out), enabled = zoom > 1f) { zoomTo(zoom / 1.6f, centre) }
            ZoomButton(Icons.Rounded.FitScreen, stringResource(R.string.pt_zoom_fit), enabled = zoom > 1f) { zoomTo(1f, centre) }
        }
    }
}

private const val MAX_ZOOM = 4f

@Composable
private fun ZoomButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f),
        shadowElevation = 2.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description) }
    }
}
