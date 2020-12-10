import React, { FunctionComponent } from "react"
import {
  ScrollView,
  Share,
  StyleSheet,
  TouchableOpacity,
  View,
} from "react-native"
import { useTranslation } from "react-i18next"
import { useNavigation } from "@react-navigation/native"

import { Text, StatusBar } from "../components"
import { useStatusBarEffect } from "../navigation"

import VaccineCard from "../Home/Cards/VaccineCard"
import AppointmentCard from "../Home/Cards/AppointmentCard"

import {
  VaccineEligibilityFlowStackScreens,
  VaccinationHistoryStackScreen,
  HomeStackScreens,
} from "../navigation"

import { Buttons, Colors, Spacing, Typography } from "../styles"
import NoVaccines from "./NoVaccines"

export const DATE_FORMAT = "ddd MMM D, YYYY"

const VaccinationHistory: FunctionComponent = () => {
  useStatusBarEffect("dark-content", Colors.background.primaryLight)
  const navigation = useNavigation()
  const { t } = useTranslation()

  const handleOnPressNewEligibilityCode = () => {
    navigation.navigate(HomeStackScreens.VaccineEligibilityStack)
  }

  return (
    <View style={style.outerContainer}>
      <StatusBar backgroundColor={Colors.background.primaryLight} />
      <ScrollView
        style={style.container}
        contentContainerStyle={style.contentContainer}
        alwaysBounceVertical={false}
      >
        <Text style={style.headerText}>
          {t("vaccination_history.vaccination_history")}
        </Text>
        <Text style={style.subHeaderText}>
          {t("vaccination_history.subtitle")}
        </Text>
        
        <VaccineCard />
        <AppointmentCard />
        <NoVaccines />   
        
      </ScrollView>
      <TouchableOpacity
        style={style.shareButton}
        onPress={handleOnPressNewEligibilityCode}
        testID="shareButton"
      >
        <Text style={style.shareButtonText}>
          {t("vaccination_history.eligibility_code")}
        </Text>
      </TouchableOpacity>
    </View>
  )
}

const style = StyleSheet.create({
  outerContainer: {
    flex: 1,
  },
  listContainer: {
    marginTop: Spacing.xxLarge,
    marginBottom: Spacing.large,
  },
  container: {
    flex: 1,
    backgroundColor: Colors.background.primaryLight,
  },
  contentContainer: {
    paddingVertical: Spacing.medium,
    paddingHorizontal: Spacing.medium,
  },
  headerText: {
    ...Typography.header.x60,
    ...Typography.style.bold,
    marginBottom: Spacing.xxxSmall,
  },
  subHeaderText: {
    ...Typography.body.x30,
    marginBottom: Spacing.large,
  },
  shareButton: {
    ...Buttons.fixedBottom.base,
  },
  shareButtonText: {
    ...Typography.button.fixedBottom,
  },
})

export default VaccinationHistory
